package com.gokulsweets.restaurant.staff.payroll.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable set compensation request data contract.
 *
 * @param effectiveFrom the effective from
 * @param dailyRate the daily rate
 * @param halfDayRate the half day rate
 */
public record SetCompensationRequest(
        @NotNull LocalDate effectiveFrom,
        @NotNull @DecimalMin("0.00") BigDecimal dailyRate,
        @NotNull @DecimalMin("0.00") BigDecimal halfDayRate) {}
