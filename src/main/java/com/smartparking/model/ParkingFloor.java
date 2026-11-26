package com.smartparking.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Groups the slots that sit on one floor. Used for the slot map and free-slot listing. */
public class ParkingFloor {

    private final int floorNumber;
    private final List<ParkingSlot> slots = new ArrayList<>();

    public ParkingFloor(int floorNumber) {
        this.floorNumber = floorNumber;
    }

    public void addSlot(ParkingSlot slot) {
        slots.add(slot);
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public List<ParkingSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    public long countFree() {
        return slots.stream().filter(s -> !s.isOccupied()).count();
    }
}
