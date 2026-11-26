package com.smartparking.threading;

import com.smartparking.model.ParkingTicket;
import com.smartparking.model.VehicleType;
import com.smartparking.service.ParkingService;

import java.util.Queue;

/**
 * One vehicle arriving at an entry gate. Runs on a gate thread from the pool,
 * calls the shared ParkingService and records the outcome in a thread-safe queue.
 */
public class EntryGateTask implements Runnable {

    private final ParkingService parkingService;
    private final String registrationNo;
    private final VehicleType vehicleType;
    private final Queue<ParkingTicket> admitted;

    public EntryGateTask(ParkingService parkingService, String registrationNo,
                         VehicleType vehicleType, Queue<ParkingTicket> admitted) {
        this.parkingService = parkingService;
        this.registrationNo = registrationNo;
        this.vehicleType = vehicleType;
        this.admitted = admitted;
    }

    @Override
    public void run() {
        String gate = Thread.currentThread().getName();
        try {
            ParkingTicket ticket = parkingService.enter(registrationNo, vehicleType);
            admitted.add(ticket);
            System.out.printf("[%s] %s admitted -> slot %s (ticket %s)%n",
                    gate, registrationNo, ticket.getSlot().getSlotId(), ticket.getTicketId());
        } catch (RuntimeException ex) {
            System.out.printf("[%s] %s rejected: %s%n", gate, registrationNo, ex.getMessage());
        }
    }
}
