package com.smartparking.repository;

import com.smartparking.model.ParkingSlot;
import com.smartparking.model.ParkingTicket;
import com.smartparking.model.Vehicle;
import com.smartparking.model.VehicleType;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** JDBC implementation of TicketRepository. Every statement is a PreparedStatement. */
@Repository
public class ParkingRepository implements TicketRepository {

    private static final String LOAD_SLOTS_SQL =
            "SELECT s.slot_id, f.floor_number, s.slot_number, s.slot_type, s.distance_rank, s.is_occupied " +
            "FROM parking_slot s JOIN parking_floor f ON s.floor_id = f.floor_id " +
            "ORDER BY f.floor_number, s.distance_rank";

    private static final String LOAD_ACTIVE_TICKETS_SQL =
            "SELECT t.ticket_id, t.vehicle_no, v.vehicle_type, t.slot_id, t.entry_time " +
            "FROM parking_ticket t JOIN vehicle v ON v.vehicle_no = t.vehicle_no " +
            "WHERE t.status = ?";

    private static final String COUNT_SLOTS_BY_TYPE_SQL =
            "SELECT slot_type, COUNT(*) FROM parking_slot GROUP BY slot_type";

    private static final String COUNT_OCCUPIED_BY_TYPE_SQL =
            "SELECT slot_type, COUNT(*) FROM parking_slot WHERE is_occupied = ? GROUP BY slot_type";

    private static final String COUNT_SESSIONS_BY_TYPE_SQL =
            "SELECT v.vehicle_type, COUNT(*) FROM parking_ticket t " +
            "JOIN vehicle v ON v.vehicle_no = t.vehicle_no GROUP BY v.vehicle_type";

    private static final String TOTAL_REVENUE_SQL =
            "SELECT COALESCE(SUM(amount), 0) FROM payment";

    private final DataSource dataSource;

    public ParkingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<ParkingSlot> loadAllSlots() {
        List<ParkingSlot> slots = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAD_SLOTS_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                slots.add(new ParkingSlot(
                        rs.getString("slot_id"),
                        rs.getInt("floor_number"),
                        rs.getInt("slot_number"),
                        VehicleType.valueOf(rs.getString("slot_type")),
                        rs.getInt("distance_rank"),
                        rs.getBoolean("is_occupied")));
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to load parking slots", ex);
        }
        return slots;
    }

    @Override
    public List<ParkingTicket> loadActiveTickets(Map<String, ParkingSlot> slotsById) {
        List<ParkingTicket> tickets = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(LOAD_ACTIVE_TICKETS_SQL)) {
            ps.setString(1, ParkingTicket.Status.ACTIVE.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String slotId = rs.getString("slot_id");
                    ParkingSlot slot = slotsById.get(slotId);
                    if (slot == null) {
                        throw new PersistenceException("Active ticket points to unknown slot " + slotId, null);
                    }
                    Vehicle vehicle = new Vehicle(
                            rs.getString("vehicle_no"),
                            VehicleType.valueOf(rs.getString("vehicle_type")));
                    LocalDateTime entry = rs.getTimestamp("entry_time").toLocalDateTime();
                    tickets.add(new ParkingTicket(rs.getString("ticket_id"), vehicle, slot, entry));
                }
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to load active tickets", ex);
        }
        return tickets;
    }

    @Override
    public void reserveSlotAndOpenTicket(Connection connection, Vehicle vehicle,
                                         ParkingSlot slot, ParkingTicket ticket) throws SQLException {
        // 1. Guard against a second active ticket for the same vehicle.
        try (PreparedStatement check = connection.prepareStatement(
                "SELECT COUNT(*) FROM parking_ticket WHERE vehicle_no = ? AND status = ?")) {
            check.setString(1, vehicle.getRegistrationNo());
            check.setString(2, ParkingTicket.Status.ACTIVE.name());
            try (ResultSet rs = check.executeQuery()) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    throw new IllegalStateException("vehicle already has an active ticket");
                }
            }
        }

        // 2. Record the vehicle (insert once, update the type if it already exists).
        try (PreparedStatement upsert = connection.prepareStatement(
                "INSERT INTO vehicle (vehicle_no, vehicle_type) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE vehicle_type = ?")) {
            upsert.setString(1, vehicle.getRegistrationNo());
            upsert.setString(2, vehicle.getType().name());
            upsert.setString(3, vehicle.getType().name());
            upsert.executeUpdate();
        }

        // 3. Conditional reservation: only flips the slot if it is still free.
        //    Exactly one row must change, otherwise another transaction got there first.
        try (PreparedStatement reserve = connection.prepareStatement(
                "UPDATE parking_slot SET is_occupied = TRUE WHERE slot_id = ? AND is_occupied = FALSE")) {
            reserve.setString(1, slot.getSlotId());
            if (reserve.executeUpdate() != 1) {
                throw new IllegalStateException("slot " + slot.getSlotId() + " was taken by another transaction");
            }
        }

        // 4. Open the ticket.
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO parking_ticket (ticket_id, vehicle_no, slot_id, entry_time, status) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            insert.setString(1, ticket.getTicketId());
            insert.setString(2, vehicle.getRegistrationNo());
            insert.setString(3, slot.getSlotId());
            insert.setTimestamp(4, Timestamp.valueOf(ticket.getEntryTime()));
            insert.setString(5, ParkingTicket.Status.ACTIVE.name());
            insert.executeUpdate();
        }
    }

    @Override
    public void closeTicketAndReleaseSlot(Connection connection, ParkingTicket ticket,
                                          BigDecimal fee, LocalDateTime exitTime) throws SQLException {
        // 1. Close the ticket, only if it is still ACTIVE.
        try (PreparedStatement close = connection.prepareStatement(
                "UPDATE parking_ticket SET exit_time = ?, amount = ?, status = ? " +
                "WHERE ticket_id = ? AND status = ?")) {
            close.setTimestamp(1, Timestamp.valueOf(exitTime));
            close.setBigDecimal(2, fee);
            close.setString(3, ParkingTicket.Status.CLOSED.name());
            close.setString(4, ticket.getTicketId());
            close.setString(5, ParkingTicket.Status.ACTIVE.name());
            if (close.executeUpdate() != 1) {
                throw new IllegalStateException("ticket " + ticket.getTicketId() + " is already closed");
            }
        }

        // 2. Record the payment.
        try (PreparedStatement pay = connection.prepareStatement(
                "INSERT INTO payment (ticket_id, amount, paid_at) VALUES (?, ?, ?)")) {
            pay.setString(1, ticket.getTicketId());
            pay.setBigDecimal(2, fee);
            pay.setTimestamp(3, Timestamp.valueOf(exitTime));
            pay.executeUpdate();
        }

        // 3. Release the slot.
        try (PreparedStatement release = connection.prepareStatement(
                "UPDATE parking_slot SET is_occupied = FALSE WHERE slot_id = ? AND is_occupied = TRUE")) {
            release.setString(1, ticket.getSlot().getSlotId());
            if (release.executeUpdate() != 1) {
                throw new IllegalStateException("slot " + ticket.getSlot().getSlotId() + " was not occupied");
            }
        }
    }

    @Override
    public Map<VehicleType, Integer> countSlotsByType() {
        return countByType(COUNT_SLOTS_BY_TYPE_SQL, null);
    }

    @Override
    public Map<VehicleType, Integer> countOccupiedSlotsByType() {
        return countByType(COUNT_OCCUPIED_BY_TYPE_SQL, Boolean.TRUE);
    }

    @Override
    public Map<VehicleType, Integer> countSessionsByType() {
        return countByType(COUNT_SESSIONS_BY_TYPE_SQL, null);
    }

    @Override
    public BigDecimal totalRevenue() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(TOTAL_REVENUE_SQL);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to compute revenue", ex);
        }
    }

    /** Runs a "type, COUNT(*)" query. Every VehicleType is present in the result, even with 0. */
    private Map<VehicleType, Integer> countByType(String sql, Boolean occupiedFlag) {
        Map<VehicleType, Integer> counts = new EnumMap<>(VehicleType.class);
        for (VehicleType type : VehicleType.values()) {
            counts.put(type, 0);
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (occupiedFlag != null) {
                ps.setBoolean(1, occupiedFlag);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(VehicleType.valueOf(rs.getString(1)), rs.getInt(2));
                }
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Failed to run count query", ex);
        }
        return counts;
    }
}
