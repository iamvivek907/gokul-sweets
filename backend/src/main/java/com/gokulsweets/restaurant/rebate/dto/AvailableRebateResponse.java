package com.gokulsweets.restaurant.rebate.dto;

import com.gokulsweets.restaurant.rebate.RebateScope;
import com.gokulsweets.restaurant.rebate.RebateType;

import java.math.BigDecimal;

/**
 * Immutable available rebate response data contract.
 *
 * @param rebateId the rebate id
 * @param code the code
 * @param name the name
 * @param description the description
 * @param scope the scope
 * @param rebateType the rebate type
 * @param rebateAmount the rebate amount
 * @param payableAfterRebate the payable after rebate
 * @param minimumOrderAmount the minimum order amount
 * @param maximumDiscountAmount the maximum discount amount
 * @param nextSlabMinimumOrderAmount the next slab minimum order amount
 * @param nextSlabRebateAmount the next slab rebate amount
 * @param amountNeededForNextSlab the amount needed for next slab
 */
public record AvailableRebateResponse(
        Long rebateId,
        String code,
        String name,
        String description,
        RebateScope scope,
        RebateType rebateType,
        BigDecimal rebateAmount,
        BigDecimal payableAfterRebate,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount,
        BigDecimal nextSlabMinimumOrderAmount,
        BigDecimal nextSlabRebateAmount,
        BigDecimal amountNeededForNextSlab) {}
