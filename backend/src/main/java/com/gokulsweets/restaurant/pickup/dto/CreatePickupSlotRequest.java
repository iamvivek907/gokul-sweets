package com.gokulsweets.restaurant.pickup.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Immutable create pickup slot request data contract.
 *
 * @param startDate the start date
 * @param endDate the end date
 * @param startTime the start time
 * @param endTime the end time
 * @param slotDurationMinutes the slot duration minutes
 * @param capacity the capacity
 * @param priorityEnabled the priority enabled
 * @param priorityCapacity the priority capacity
 * @param priorityCharge the priority charge
 */
public record CreatePickupSlotRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @Min(1) Integer slotDurationMinutes,
        @NotNull @Min(1) Integer capacity,
        Boolean priorityEnabled,
        @Min(0) Integer priorityCapacity,
        @DecimalMin(value = "0.00") BigDecimal priorityCharge) {}
