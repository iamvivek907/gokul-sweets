package com.gokulsweets.restaurant.rebate.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Immutable rebate slab request data contract.
 *
 * @param minimumOrderAmount the minimum order amount
 * @param rebateAmount the rebate amount
 */
public record RebateSlabRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal minimumOrderAmount,
        @NotNull @DecimalMin(value = "0.01") BigDecimal rebateAmount) {}
