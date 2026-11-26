package com.smartparking.service;

import com.smartparking.model.VehicleType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/** Fee calculation. RegularBillingService and WeekendBillingService are the two implementations. */
public interface BillingService {

    BigDecimal calculateFee(VehicleType type, LocalDateTime entryTime, LocalDateTime exitTime);

    /**
     * Billable hours: any started hour is charged, with a minimum of one hour.
     * Example: 35 minutes -> 1 hour, 61 minutes -> 2 hours.
     */
    default long billableHours(LocalDateTime entryTime, LocalDateTime exitTime) {
        long minutes = Math.max(1, Duration.between(entryTime, exitTime).toMinutes());
        return (minutes + 59) / 60;
    }
}
