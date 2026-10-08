package com.gokulsweets.restaurant.staff.payroll.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Immutable update payment request data contract. */
public record UpdatePaymentRequest(
        @NotNull Long branchId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Size(max = 1000) String note) {}
