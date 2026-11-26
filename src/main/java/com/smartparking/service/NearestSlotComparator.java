package com.smartparking.service;

import com.smartparking.model.ParkingSlot;

import java.util.Comparator;

/**
 * Defines "nearest" for the allocator: lowest floor first, then closest distance rank
 * on that floor, then slot number as a final tie-breaker so the result is deterministic.
 */
public final class NearestSlotComparator implements Comparator<ParkingSlot> {

    public static final NearestSlotComparator INSTANCE = new NearestSlotComparator();

    private NearestSlotComparator() {
    }

    @Override
    public int compare(ParkingSlot a, ParkingSlot b) {
        int byFloor = Integer.compare(a.getFloorNumber(), b.getFloorNumber());
        if (byFloor != 0) {
            return byFloor;
        }
        int byRank = Integer.compare(a.getDistanceRank(), b.getDistanceRank());
        if (byRank != 0) {
            return byRank;
        }
        return Integer.compare(a.getSlotNumber(), b.getSlotNumber());
    }
}
