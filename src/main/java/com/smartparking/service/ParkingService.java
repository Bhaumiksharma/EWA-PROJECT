package com.smartparking.service;

import com.smartparking.model.ParkingFloor;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.ParkingTicket;
import com.smartparking.model.Vehicle;
import com.smartparking.model.VehicleType;
import com.smartparking.repository.PersistenceException;
import com.smartparking.repository.TicketRepository;
import com.smartparking.repository.TransactionRunner;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Coordinates entry and exit at all gates.
 *
 * Concurrency: every entry and exit runs while holding one ReentrantLock. The critical section
 * covers the free-slot search, the database transaction and the in-memory update, so two gate
 * threads can never pick the same slot. The conditional UPDATE in the repository is a second
 * guard, in case another process is using the same database.
 *
 * In-memory state (all guarded by the lock):
 *   slotsById            HashMap  - slot id -> slot, for direct lookup
 *   slotList             ArrayList - all slots, kept in nearest-first order
 *   activeVehicles       HashSet  - registration numbers that are currently parked
 *   ticketsByVehicle     HashMap  - registration number -> active ticket
 */
@Service
public class ParkingService implements InitializingBean {

    private static final Pattern REG_NO_PATTERN = Pattern.compile("^[A-Z0-9-]{4,15}$");

    private final TicketRepository repository;
    private final TransactionRunner transactions;
    private final SlotAllocationService allocator;
    private final BillingService billing;

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, ParkingSlot> slotsById = new HashMap<>();
    private final List<ParkingSlot> slotList = new ArrayList<>();
    private final Set<String> activeVehicles = new HashSet<>();
    private final Map<String, ParkingTicket> ticketsByVehicle = new HashMap<>();

    // Set from the console menu to demonstrate that a failed entry is rolled back.
    private final AtomicBoolean faultOnNextEntry = new AtomicBoolean(false);

    public ParkingService(TicketRepository repository,
                          TransactionRunner transactions,
                          SlotAllocationService allocator,
                          @Qualifier("billingService") BillingService billing) {
        this.repository = repository;
        this.transactions = transactions;
        this.allocator = allocator;
        this.billing = billing;
    }

    /** Runs once after Spring has injected the dependencies: loads slots and open tickets from MySQL. */
    @Override
    public void afterPropertiesSet() {
        reloadState();
    }

    public void reloadState() {
        lock.lock();
        try {
            slotsById.clear();
            slotList.clear();
            activeVehicles.clear();
            ticketsByVehicle.clear();

            for (ParkingSlot slot : repository.loadAllSlots()) {
                slotsById.put(slot.getSlotId(), slot);
                slotList.add(slot);
            }
            slotList.sort(NearestSlotComparator.INSTANCE);

            for (ParkingTicket ticket : repository.loadActiveTickets(slotsById)) {
                String regNo = ticket.getVehicle().getRegistrationNo();
                activeVehicles.add(regNo);
                ticketsByVehicle.put(regNo, ticket);
            }
        } finally {
            lock.unlock();
        }
    }

    /** Entry gate: validates the vehicle, allocates the nearest compatible slot and issues a ticket. */
    public ParkingTicket enter(String rawRegNo, VehicleType type) {
        String regNo = normalizeRegNo(rawRegNo);
        if (type == null) {
            throw new ParkingException("Vehicle type is required (BIKE, CAR or EV)");
        }

        lock.lock();
        try {
            if (activeVehicles.contains(regNo)) {
                throw new ParkingException("Vehicle " + regNo + " is already parked");
            }

            ParkingSlot slot = allocator.findNearestFreeSlot(type, slotList)
                    .orElseThrow(() -> new ParkingException("No free compatible slot for " + type));

            Vehicle vehicle = new Vehicle(regNo, type);
            ParkingTicket ticket = new ParkingTicket(newTicketId(), vehicle, slot, LocalDateTime.now());

            try {
                transactions.inTransaction(conn -> {
                    repository.reserveSlotAndOpenTicket(conn, vehicle, slot, ticket);
                    maybeInjectFault();
                    return null;
                });
            } catch (PersistenceException | IllegalStateException ex) {
                // Nothing in memory has changed yet, so the state is still consistent.
                throw new ParkingException("Entry rejected and rolled back: " + ex.getMessage(), ex);
            }

            // Commit succeeded, so now it is safe to update the in-memory state.
            slot.setOccupied(true);
            activeVehicles.add(regNo);
            ticketsByVehicle.put(regNo, ticket);
            return ticket;
        } finally {
            lock.unlock();
        }
    }

    /** Exit gate: closes the active ticket, computes the fee, records the payment and frees the slot. */
    public ParkingTicket exit(String rawRegNo) {
        String regNo = normalizeRegNo(rawRegNo);

        lock.lock();
        try {
            ParkingTicket ticket = ticketsByVehicle.get(regNo);
            if (ticket == null) {
                throw new ParkingException("No active ticket found for " + regNo);
            }

            LocalDateTime exitTime = LocalDateTime.now();
            BigDecimal fee = billing.calculateFee(ticket.getVehicle().getType(), ticket.getEntryTime(), exitTime);

            try {
                transactions.inTransaction(conn -> {
                    repository.closeTicketAndReleaseSlot(conn, ticket, fee, exitTime);
                    return null;
                });
            } catch (PersistenceException | IllegalStateException ex) {
                throw new ParkingException("Exit rejected and rolled back: " + ex.getMessage(), ex);
            }

            ticket.close(exitTime, fee);
            ticket.getSlot().setOccupied(false);
            activeVehicles.remove(regNo);
            ticketsByVehicle.remove(regNo);
            return ticket;
        } finally {
            lock.unlock();
        }
    }

    /** Snapshot of the slot map grouped by floor, nearest slots first. */
    public List<ParkingFloor> getFloors() {
        lock.lock();
        try {
            Map<Integer, ParkingFloor> floors = new TreeMap<>();
            for (ParkingSlot slot : slotList) {
                floors.computeIfAbsent(slot.getFloorNumber(), ParkingFloor::new).addSlot(slot);
            }
            return new ArrayList<>(floors.values());
        } finally {
            lock.unlock();
        }
    }

    /** The next entry will fail after the slot has been reserved, so the rollback can be shown. */
    public void injectFaultOnNextEntry() {
        faultOnNextEntry.set(true);
    }

    private void maybeInjectFault() throws SQLException {
        if (faultOnNextEntry.getAndSet(false)) {
            throw new SQLException("injected fault after slot reservation (test)");
        }
    }

    private String normalizeRegNo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ParkingException("Vehicle number cannot be empty");
        }
        String regNo = raw.trim().toUpperCase(Locale.ROOT);
        if (!REG_NO_PATTERN.matcher(regNo).matches()) {
            throw new ParkingException("Invalid vehicle number '" + raw.trim() + "' (4-15 letters, digits or '-')");
        }
        return regNo;
    }

    private String newTicketId() {
        return "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
