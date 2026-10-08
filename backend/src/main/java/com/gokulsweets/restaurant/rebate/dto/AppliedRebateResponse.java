package com.gokulsweets.restaurant.rebate.dto;

import java.math.BigDecimal;

/** Immutable applied rebate response data contract. */
public record AppliedRebateResponse(
        String orderNumber,
        String rebateCode,
        String rebateName,
        BigDecimal rebateAmount,
        BigDecimal amountBeforeRebate,
        BigDecimal totalAmount,
        BigDecimal paymentFee,
        BigDecimal paymentFeeTax,
        BigDecimal paymentFeeRate) {

    public AppliedRebateResponse(
            String orderNumber,
            String rebateCode,
            String rebateName,
            BigDecimal rebateAmount,
            BigDecimal amountBeforeRebate,
            BigDecimal totalAmount) {
        this(
                orderNumber,
                rebateCode,
                rebateName,
                rebateAmount,
                amountBeforeRebate,
                totalAmount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
