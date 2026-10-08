package com.gokulsweets.restaurant.pickup.dto;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.pickup.BranchPickupSettings;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Immutable branch pickup settings response data contract. */
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
        BigDecimal defaultPriorityCharge) {

    /**
     * Froms the operation.
     *
     * @param settings the settings
     * @return the from result
     */
    public static BranchPickupSettingsResponse from(BranchPickupSettings settings) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BranchPickupSettingsResponse.class, "from(BranchPickupSettings)");
        try {
            return new BranchPickupSettingsResponse(
                    settings.getId(),
                    settings.getBranch().getId(),
                    settings.getSlotDurationMinutes(),
                    settings.getDefaultCapacity(),
                    settings.getAdvanceBookingDays(),
                    settings.getOpeningTime(),
                    settings.getClosingTime(),
                    settings.isEnabled(),
                    settings.isPriorityEnabled(),
                    settings.getDefaultPriorityCapacity(),
                    settings.getDefaultPriorityCharge());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchPickupSettingsResponse.class,
                    "from(BranchPickupSettings)");
        }
    }
}
