package com.gokulsweets.restaurant.pickup.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Immutable update branch pickup settings request data contract.
 *
 * @param slotDurationMinutes the slot duration minutes
 * @param defaultCapacity the default capacity
 * @param advanceBookingDays the advance booking days
 * @param openingTime the opening time
 * @param closingTime the closing time
 * @param enabled the enabled
 * @param priorityEnabled the priority enabled
 * @param defaultPriorityCapacity the default priority capacity
 * @param defaultPriorityCharge the default priority charge
 */
public record UpdateBranchPickupSettingsRequest(
        @NotNull @Min(1) Integer slotDurationMinutes,
        @NotNull @Min(1) Integer defaultCapacity,
        @NotNull @Min(0) Integer advanceBookingDays,
        @NotNull LocalTime openingTime,
        @NotNull LocalTime closingTime,
        @NotNull Boolean enabled,
        @NotNull Boolean priorityEnabled,
        @NotNull @Min(0) Integer defaultPriorityCapacity,
        @NotNull @DecimalMin(value = "0.00") BigDecimal defaultPriorityCharge) {}
