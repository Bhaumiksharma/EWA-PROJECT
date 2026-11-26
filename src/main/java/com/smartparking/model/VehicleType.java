package com.smartparking.model;

import java.util.List;

/**
 * The three vehicle / slot categories.
 * hourlyRate is the base rate used by the regular billing policy (in INR).
 */
public enum VehicleType {
    BIKE(20),
    CAR(40),
    EV(30);

    private final int hourlyRate;

    VehicleType(int hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public int getHourlyRate() {
        return hourlyRate;
    }

    /**
     * Slot types this vehicle can use, in order of preference.
     * An EV prefers an EV slot and may fall back to a CAR slot when no EV slot is free.
     * Bikes and cars only use their own slot type.
     */
    public List<VehicleType> compatibleSlotTypes() {
        switch (this) {
            case EV:
                return List.of(EV, CAR);
            default:
                return List.of(this);
        }
    }
}
