package com.gokulsweets.restaurant.pickup.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

public record UpdateBranchPickupSettingsRequest(

        @NotNull
        @Min(1)
        Integer slotDurationMinutes,

        @NotNull
        @Min(1)
        Integer defaultCapacity,

        @NotNull
        @Min(0)
        Integer advanceBookingDays,

        @NotNull
        LocalTime openingTime,

        @NotNull
        LocalTime closingTime,

        @NotNull
        Boolean enabled,

        @NotNull
        Boolean priorityEnabled,

        @NotNull
        @Min(0)
        Integer defaultPriorityCapacity,

        @NotNull
        @DecimalMin(value = "0.00")
        BigDecimal defaultPriorityCharge
) {
}