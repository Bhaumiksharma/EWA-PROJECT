package com.smartparking.model;

/**
 * One parking slot. distanceRank is the slot's position from the entry gate on its floor
 * (1 = closest). Occupancy is only changed while the ParkingService lock is held.
 */
public class ParkingSlot {

    private final String slotId;
    private final int floorNumber;
    private final int slotNumber;
    private final VehicleType slotType;
    private final int distanceRank;
    private boolean occupied;

    public ParkingSlot(String slotId, int floorNumber, int slotNumber,
                       VehicleType slotType, int distanceRank, boolean occupied) {
        this.slotId = slotId;
        this.floorNumber = floorNumber;
        this.slotNumber = slotNumber;
        this.slotType = slotType;
        this.distanceRank = distanceRank;
        this.occupied = occupied;
    }

    public String getSlotId() {
        return slotId;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    public VehicleType getSlotType() {
        return slotType;
    }

    public int getDistanceRank() {
        return distanceRank;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    @Override
    public String toString() {
        return slotId + " [" + slotType + ", rank " + distanceRank + (occupied ? ", OCCUPIED]" : ", FREE]");
    }
}
