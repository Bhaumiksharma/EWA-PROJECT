package com.smartparking.service;

import com.smartparking.model.ParkingFloor;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.VehicleType;
import com.smartparking.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds the four required reports. Totals and revenue come from SQL; the free-slot list comes from memory. */
@Service
public class ReportService {

    private final TicketRepository repository;
    private final ParkingService parkingService;

    public ReportService(TicketRepository repository, ParkingService parkingService) {
        this.repository = repository;
        this.parkingService = parkingService;
    }

    public String buildReport() {
        Map<VehicleType, Integer> totalByType = repository.countSlotsByType();
        Map<VehicleType, Integer> occupiedByType = repository.countOccupiedSlotsByType();
        Map<VehicleType, Integer> sessionsByType = repository.countSessionsByType();
        BigDecimal revenue = repository.totalRevenue();

        int total = sum(totalByType);
        int occupied = sum(occupiedByType);
        int free = total - occupied;

        StringBuilder sb = new StringBuilder();

        sb.append(String.format("%n--- 1. Occupancy ---%n"));
        sb.append(String.format("Total slots : %d%n", total));
        sb.append(String.format("Occupied    : %d%n", occupied));
        sb.append(String.format("Free        : %d%n", free));
        sb.append(String.format("Occupancy   : %.1f%%%n", percent(occupied, total)));

        sb.append(String.format("%n--- 2. Free slots (nearest first) ---%n"));
        for (ParkingFloor floor : parkingService.getFloors()) {
            List<String> freeIds = floor.getSlots().stream()
                    .filter(s -> !s.isOccupied())
                    .map(ParkingSlot::getSlotId)
                    .collect(Collectors.toList());
            sb.append(String.format("Floor %d : %s%n", floor.getFloorNumber(),
                    freeIds.isEmpty() ? "none" : String.join(", ", freeIds)));
        }

        sb.append(String.format("%n--- 3. Vehicle-type-wise utilization ---%n"));
        sb.append("Current % = occupied slots of that type right now / slots of that type.\n");
        sb.append("Sessions  = all tickets ever issued for that vehicle type (history).\n");
        sb.append(String.format("%-6s %-7s %-9s %-10s %-9s%n", "Type", "Slots", "Occupied", "Current %", "Sessions"));
        for (VehicleType type : VehicleType.values()) {
            int typeTotal = totalByType.getOrDefault(type, 0);
            int typeOccupied = occupiedByType.getOrDefault(type, 0);
            sb.append(String.format("%-6s %-7d %-9d %-10.1f %-9d%n",
                    type, typeTotal, typeOccupied, percent(typeOccupied, typeTotal),
                    sessionsByType.getOrDefault(type, 0)));
        }

        sb.append(String.format("%n--- 4. Revenue ---%n"));
        sb.append(String.format("Total revenue (all recorded payments) : INR %s%n",
                revenue.setScale(2, RoundingMode.HALF_UP)));

        return sb.toString();
    }

    private static int sum(Map<VehicleType, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static double percent(int part, int whole) {
        return whole == 0 ? 0.0 : (part * 100.0) / whole;
    }
}
