package com.gokulsweets.restaurant.staff.payroll.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable set payroll opening balance request data contract.
 *
 * @param asOfDate the as of date
 * @param earnedAmount the earned amount
 * @param takenAmount the taken amount
 * @param note the note
 */
public record SetPayrollOpeningBalanceRequest(
        @NotNull LocalDate asOfDate,
        @NotNull @DecimalMin("0.00") BigDecimal earnedAmount,
        @NotNull @DecimalMin("0.00") BigDecimal takenAmount,
        @Size(max = 1000) String note) {}
