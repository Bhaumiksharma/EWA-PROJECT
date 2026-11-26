package com.smartparking.service;

import com.smartparking.model.ParkingSlot;
import com.smartparking.model.VehicleType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Chooses which free slot a vehicle should get.
 * Rule: try the vehicle's own slot type first, then its fallback types (EV -> CAR).
 * Within a type, the nearest slot wins (see NearestSlotComparator).
 * This class does not change any state; the caller must hold the ParkingService lock.
 */
@Service
public class SlotAllocationService {

    public Optional<ParkingSlot> findNearestFreeSlot(VehicleType vehicleType, List<ParkingSlot> slots) {
        for (VehicleType slotType : vehicleType.compatibleSlotTypes()) {
            Optional<ParkingSlot> match = slots.stream()
                    .filter(s -> s.getSlotType() == slotType && !s.isOccupied())
                    .min(NearestSlotComparator.INSTANCE);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }
}
