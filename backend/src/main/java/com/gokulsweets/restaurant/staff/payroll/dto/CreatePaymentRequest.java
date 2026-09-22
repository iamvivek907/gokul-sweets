package com.gokulsweets.restaurant.staff.payroll.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePaymentRequest(

        @NotNull
        Long branchId,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount,

        @Size(max = 1000)
        String note
) {
}
