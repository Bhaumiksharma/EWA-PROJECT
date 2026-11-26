package com.smartparking.service;

import com.smartparking.model.VehicleType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Flat hourly rate per vehicle type, every day of the week. */
@Service("regularBillingService")
public class RegularBillingService implements BillingService {

    @Override
    public BigDecimal calculateFee(VehicleType type, LocalDateTime entryTime, LocalDateTime exitTime) {
        long hours = billableHours(entryTime, exitTime);
        return BigDecimal.valueOf(hours * type.getHourlyRate()).setScale(2);
    }
}
