package com.gokulsweets.restaurant.staff.payroll.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SetPayrollOpeningBalanceRequest(

        @NotNull
        LocalDate asOfDate,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal earnedAmount,

        @NotNull
        @DecimalMin("0.00")
        BigDecimal takenAmount,

        @Size(max = 1000)
        String note
) {
}
