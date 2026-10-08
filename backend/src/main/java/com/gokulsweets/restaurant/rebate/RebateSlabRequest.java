package com.gokulsweets.restaurant.rebate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Immutable rebate slab request data contract. */
public record RebateSlabRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal minimumOrderAmount,
        @NotNull @DecimalMin(value = "0.01") BigDecimal rebateAmount) {}
