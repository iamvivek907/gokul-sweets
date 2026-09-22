package com.gokulsweets.restaurant.rebate.dto;

import com.gokulsweets.restaurant.rebate.RebateScope;
import com.gokulsweets.restaurant.rebate.RebateType;

import java.math.BigDecimal;

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

        BigDecimal amountNeededForNextSlab

) {
}