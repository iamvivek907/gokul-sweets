package com.gokulsweets.restaurant.pickup.dto;

import com.gokulsweets.restaurant.pickup.BranchPickupSettings;

import java.math.BigDecimal;
import java.time.LocalTime;

public record BranchPickupSettingsResponse(

        Long id,

        Long branchId,

        Integer slotDurationMinutes,

        Integer defaultCapacity,

        Integer advanceBookingDays,

        LocalTime openingTime,

        LocalTime closingTime,

        boolean enabled,

        boolean priorityEnabled,

        Integer defaultPriorityCapacity,

        BigDecimal defaultPriorityCharge
) {

    public static BranchPickupSettingsResponse from(
            BranchPickupSettings settings
    ) {

        return new BranchPickupSettingsResponse(

                settings.getId(),

                settings.getBranch()
                        .getId(),

                settings.getSlotDurationMinutes(),

                settings.getDefaultCapacity(),

                settings.getAdvanceBookingDays(),

                settings.getOpeningTime(),

                settings.getClosingTime(),

                settings.isEnabled(),

                settings.isPriorityEnabled(),

                settings.getDefaultPriorityCapacity(),

                settings.getDefaultPriorityCharge()
        );
    }
}