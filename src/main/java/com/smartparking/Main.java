package com.smartparking;

import com.smartparking.config.AppConfig;
import com.smartparking.model.ParkingFloor;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.ParkingTicket;
import com.smartparking.model.VehicleType;
import com.smartparking.repository.PersistenceException;
import com.smartparking.service.BillingService;
import com.smartparking.service.ParkingException;
import com.smartparking.service.ParkingService;
import com.smartparking.service.ReportService;
import com.smartparking.service.WeekendBillingService;
import com.smartparking.threading.EntryGateTask;
import com.smartparking.threading.GateThreadFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Starts the Spring context and runs the console menu. */
public class Main {

    private static final int GATE_COUNT = 3;
    private static final int SIMULATED_VEHICLES = 12;

    public static void main(String[] args) {
        System.out.println("=== Smart Parking Allocation System ===");
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(AppConfig.class)) {

            ParkingService parking = context.getBean(ParkingService.class);
            ReportService reports = context.getBean(ReportService.class);
            BillingService billing = context.getBean("billingService", BillingService.class);
            Scanner in = new Scanner(System.in);

            boolean running = true;
            while (running && in.hasNextLine()) {
                printMenu();
                String choice = in.nextLine().trim();
                try {
                    switch (choice) {
                        case "1" -> handleEntry(parking, in);
                        case "2" -> handleExit(parking, billing, in);
                        case "3" -> showSlots(parking);
                        case "4" -> System.out.print(reports.buildReport());
                        case "5" -> runConcurrentSimulation(parking);
                        case "6" -> {
                            parking.injectFaultOnNextEntry();
                            System.out.println("The next entry will fail after the slot is reserved.");
                            System.out.println("Expected result: entry rolled back, slot stays FREE, no ticket row.");
                        }
                        case "7" -> showTariff(billing);
                        case "0" -> running = false;
                        default -> System.out.println("Unknown option, try again.");
                    }
                } catch (ParkingException | PersistenceException ex) {
                    System.out.println("Error: " + ex.getMessage());
                }
                System.out.println();
            }
        }
        System.out.println("Goodbye.");
    }

    private static void printMenu() {
        System.out.println("1. Park a vehicle (entry gate)");
        System.out.println("2. Exit and pay");
        System.out.println("3. Show slot map");
        System.out.println("4. Reports");
        System.out.println("5. Run concurrent entry simulation (" + GATE_COUNT + " gates)");
        System.out.println("6. Inject fault on next entry (rollback test)");
        System.out.println("7. View tariff and rates");
        System.out.println("0. Quit");
        System.out.print("Choose: ");
    }

    private static void handleEntry(ParkingService parking, Scanner in) {
        System.out.print("Vehicle number (e.g. DL01AB1234): ");
        String regNo = in.nextLine();
        VehicleType type = readVehicleType(in);

        ParkingTicket ticket = parking.enter(regNo, type);
        System.out.printf("Ticket %s issued. Park at slot %s (floor %d).%n",
                ticket.getTicketId(), ticket.getSlot().getSlotId(), ticket.getSlot().getFloorNumber());
    }

    private static void handleExit(ParkingService parking, BillingService billing, Scanner in) {
        System.out.print("Vehicle number: ");
        ParkingTicket ticket = parking.exit(in.nextLine());

        long minutes = Math.max(1, ticket.getParkedMinutes());
        long hours = (minutes + 59) / 60;
        DayOfWeek entryDay = ticket.getEntryTime().getDayOfWeek();
        boolean weekendSurcharge = billing instanceof WeekendBillingService
                && (entryDay == DayOfWeek.SATURDAY || entryDay == DayOfWeek.SUNDAY);

        System.out.println("--- Receipt ---");
        System.out.println("Ticket  : " + ticket.getTicketId());
        System.out.println("Vehicle : " + ticket.getVehicle());
        System.out.println("Slot    : " + ticket.getSlot().getSlotId() + " (now free)");
        System.out.println("Entered : " + ticket.getEntryTime());
        System.out.println("Exited  : " + ticket.getExitTime());
        System.out.println("Parked  : " + ticket.getParkedMinutes() + " min");
        System.out.println("Rate    : Rs " + ticket.getVehicle().getType().getHourlyRate()
                + " per hour x " + hours + " hour(s)");
        if (weekendSurcharge) {
            System.out.println("Weekend : +50% surcharge (entered on " + entryDay + ")");
        }
        System.out.println("Amount  : INR " + ticket.getAmount());
    }

    private static VehicleType readVehicleType(Scanner in) {
        System.out.print("Vehicle type (BIKE / CAR / EV): ");
        String raw = in.nextLine().trim().toUpperCase(Locale.ROOT);
        try {
            return VehicleType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ParkingException("Unknown vehicle type '" + raw + "'");
        }
    }

    private static void showTariff(BillingService billing) {
        System.out.println("--- Parking tariff ---");
        for (VehicleType type : VehicleType.values()) {
            System.out.printf("%-5s Rs %d per hour%n", type, type.getHourlyRate());
        }
        System.out.println("Any part of an hour is charged as a full hour (minimum 1 hour).");
        if (billing instanceof WeekendBillingService) {
            System.out.println("Weekend policy is active: 50% extra for vehicles entering on Saturday or Sunday.");
        } else {
            System.out.println("Regular policy is active: same rate every day of the week.");
        }
    }

    private static void showSlots(ParkingService parking) {
        for (ParkingFloor floor : parking.getFloors()) {
            System.out.printf("Floor %d  (free %d of %d)%n",
                    floor.getFloorNumber(), floor.countFree(), floor.getSlots().size());
            for (ParkingSlot slot : floor.getSlots()) {
                System.out.printf("  %-6s %-5s rank %-2d %s%n",
                        slot.getSlotId(), slot.getSlotType(), slot.getDistanceRank(),
                        slot.isOccupied() ? "OCCUPIED" : "FREE");
            }
        }
    }

    /**
     * Sends random vehicles through a pool of gate threads at the same time,
     * then checks that no slot was handed to two vehicles.
     */
    private static void runConcurrentSimulation(ParkingService parking) {
        System.out.printf("Starting %d gate threads with %d arriving vehicles...%n", GATE_COUNT, SIMULATED_VEHICLES);

        ExecutorService gates = Executors.newFixedThreadPool(GATE_COUNT, new GateThreadFactory());
        ConcurrentLinkedQueue<ParkingTicket> admitted = new ConcurrentLinkedQueue<>();
        Random random = new Random();
        VehicleType[] types = VehicleType.values();

        for (int i = 0; i < SIMULATED_VEHICLES; i++) {
            String regNo = "SIM" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
            VehicleType type = types[random.nextInt(types.length)];
            gates.submit(new EntryGateTask(parking, regNo, type, admitted));
        }

        gates.shutdown();
        try {
            if (!gates.awaitTermination(60, TimeUnit.SECONDS)) {
                gates.shutdownNow();
                System.out.println("Warning: simulation did not finish within 60 seconds.");
            }
        } catch (InterruptedException ex) {
            gates.shutdownNow();
            Thread.currentThread().interrupt();
        }

        Set<String> slotIds = new HashSet<>();
        int doubleAllocated = 0;
        for (ParkingTicket ticket : admitted) {
            if (!slotIds.add(ticket.getSlot().getSlotId())) {
                doubleAllocated++;
            }
        }
        System.out.printf("Admitted %d, rejected %d.%n", admitted.size(), SIMULATED_VEHICLES - admitted.size());
        System.out.printf("Slots allocated twice: %d %s%n", doubleAllocated,
                doubleAllocated == 0 ? "(OK)" : "(BUG - investigate)");
    }
}