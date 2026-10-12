package com.gokulsweets.restaurant.rebate.dto;

import java.math.BigDecimal;

/**
 * Immutable rebate slab response data contract.
 *
 * @param minimumOrderAmount the minimum order amount
 * @param rebateAmount the rebate amount
 */
public record RebateSlabResponse(BigDecimal minimumOrderAmount, BigDecimal rebateAmount) {}
