package com.gokulsweets.restaurant.pickup.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Immutable update pickup slot request data contract.
 *
 * @param slotDate the slot date
 * @param startTime the start time
 * @param endTime the end time
 * @param capacity the capacity
 * @param active the active
 * @param priorityEnabled the priority enabled
 * @param priorityCapacity the priority capacity
 * @param priorityCharge the priority charge
 */
public record UpdatePickupSlotRequest(
        @NotNull LocalDate slotDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @Min(1) Integer capacity,
        @NotNull Boolean active,
        Boolean priorityEnabled,
        @Min(0) Integer priorityCapacity,
        @DecimalMin(value = "0.00") BigDecimal priorityCharge) {}
