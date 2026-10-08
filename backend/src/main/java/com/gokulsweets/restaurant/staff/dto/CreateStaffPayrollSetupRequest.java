package com.gokulsweets.restaurant.staff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable create staff payroll setup request data contract.
 *
 * @param effectiveFrom the effective from
 * @param dailyRate the daily rate
 * @param halfDayRate the half day rate
 * @param openingBalance the opening balance
 */
public record CreateStaffPayrollSetupRequest(
        @NotNull LocalDate effectiveFrom,
        @NotNull @DecimalMin("0.00") BigDecimal dailyRate,
        @NotNull @DecimalMin("0.00") BigDecimal halfDayRate,
        @Valid CreateStaffOpeningBalanceRequest openingBalance) {}
