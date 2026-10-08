package com.gokulsweets.restaurant.rebate.dto;

import java.math.BigDecimal;

/**
 * Immutable applied rebate response data contract.
 *
 * @param orderNumber the order number
 * @param rebateCode the rebate code
 * @param rebateName the rebate name
 * @param rebateAmount the rebate amount
 * @param amountBeforeRebate the amount before rebate
 * @param totalAmount the total amount
 * @param paymentFee the payment fee
 * @param paymentFeeTax the payment fee tax
 * @param paymentFeeRate the payment fee rate
 */
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

    /**
     * Creates a applied rebate response instance.
     *
     * @param orderNumber the order number
     * @param rebateCode the rebate code
     * @param rebateName the rebate name
     * @param rebateAmount the rebate amount
     * @param amountBeforeRebate the amount before rebate
     * @param totalAmount the total amount
     */
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
