package com.gokulsweets.restaurant.rebate.dto;

import java.math.BigDecimal;

/** Immutable rebate slab response data contract. */
public record RebateSlabResponse(BigDecimal minimumOrderAmount, BigDecimal rebateAmount) {}
