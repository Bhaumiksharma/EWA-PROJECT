package com.smartparking.service;

import com.smartparking.model.VehicleType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * Same hourly rates as the regular policy, plus a 50% surcharge when the
 * vehicle enters on a Saturday or Sunday. The day is taken from the entry time.
 */
@Service("weekendBillingService")
public class WeekendBillingService implements BillingService {

    private static final BigDecimal WEEKEND_MULTIPLIER = new BigDecimal("1.50");

    @Override
    public BigDecimal calculateFee(VehicleType type, LocalDateTime entryTime, LocalDateTime exitTime) {
        long hours = billableHours(entryTime, exitTime);
        BigDecimal fee = BigDecimal.valueOf(hours * type.getHourlyRate());
        if (isWeekend(entryTime)) {
            fee = fee.multiply(WEEKEND_MULTIPLIER);
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isWeekend(LocalDateTime time) {
        DayOfWeek day = time.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
