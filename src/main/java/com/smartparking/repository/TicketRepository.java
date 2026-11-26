package com.smartparking.repository;

import com.smartparking.model.ParkingSlot;
import com.smartparking.model.ParkingTicket;
import com.smartparking.model.Vehicle;
import com.smartparking.model.VehicleType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Persistence contract for slots, tickets, payments and report queries.
 * ParkingService depends on this interface; ParkingRepository is the JDBC implementation.
 * The two write methods take a Connection so the caller can group them in one transaction.
 */
public interface TicketRepository {

    List<ParkingSlot> loadAllSlots();

    List<ParkingTicket> loadActiveTickets(Map<String, ParkingSlot> slotsById);

    /** Entry transaction part: vehicle row, conditional slot reservation, new ACTIVE ticket. */
    void reserveSlotAndOpenTicket(Connection connection, Vehicle vehicle,
                                  ParkingSlot slot, ParkingTicket ticket) throws SQLException;

    /** Exit transaction part: close ticket, insert payment, release slot. */
    void closeTicketAndReleaseSlot(Connection connection, ParkingTicket ticket,
                                   BigDecimal fee, LocalDateTime exitTime) throws SQLException;

    Map<VehicleType, Integer> countSlotsByType();

    Map<VehicleType, Integer> countOccupiedSlotsByType();

    Map<VehicleType, Integer> countSessionsByType();

    BigDecimal totalRevenue();
}
