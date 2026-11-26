package com.smartparking.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * A parking session: which vehicle is in which slot, and since when.
 * The exit fields stay null until the ticket is closed.
 */
public class ParkingTicket {

    public enum Status { ACTIVE, CLOSED }

    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSlot slot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private BigDecimal amount;
    private Status status = Status.ACTIVE;

    public ParkingTicket(String ticketId, Vehicle vehicle, ParkingSlot slot, LocalDateTime entryTime) {
        this.ticketId = ticketId;
        this.vehicle = vehicle;
        this.slot = slot;
        this.entryTime = entryTime;
    }

    public void close(LocalDateTime exitTime, BigDecimal amount) {
        this.exitTime = exitTime;
        this.amount = amount;
        this.status = Status.CLOSED;
    }

    public String getTicketId() {
        return ticketId;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public ParkingSlot getSlot() {
        return slot;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }

    public long getParkedMinutes() {
        if (exitTime == null) {
            return 0;
        }
        return Duration.between(entryTime, exitTime).toMinutes();
    }
}
