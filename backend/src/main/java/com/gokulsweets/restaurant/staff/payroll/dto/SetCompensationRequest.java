package com.gokulsweets.restaurant.staff.payroll.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SetCompensationRequest(

        @NotNull
        LocalDate effectiveFrom,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal dailyRate,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal halfDayRate
) {
}
