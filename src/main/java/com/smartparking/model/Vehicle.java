package com.smartparking.model;

import java.util.Objects;

public class Vehicle {

    private final String registrationNo;
    private final VehicleType type;

    public Vehicle(String registrationNo, VehicleType type) {
        this.registrationNo = registrationNo;
        this.type = type;
    }

    public String getRegistrationNo() {
        return registrationNo;
    }

    public VehicleType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vehicle)) return false;
        Vehicle other = (Vehicle) o;
        return registrationNo.equals(other.registrationNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registrationNo);
    }

    @Override
    public String toString() {
        return registrationNo + " (" + type + ")";
    }
}
