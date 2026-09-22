package com.gokulsweets.restaurant.pickup.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpdatePickupSlotRequest(
        @NotNull LocalDate slotDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,

        @NotNull
        @Min(1)
        Integer capacity,

        @NotNull
        Boolean active,

        Boolean priorityEnabled,

        @Min(0)
        Integer priorityCapacity,

        @DecimalMin(value = "0.00")
        BigDecimal priorityCharge
) {
}