package com.gokulsweets.restaurant.staff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateStaffPayrollSetupRequest(

        @NotNull
        LocalDate effectiveFrom,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal dailyRate,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal halfDayRate,

        @Valid
        CreateStaffOpeningBalanceRequest openingBalance
) {
}
