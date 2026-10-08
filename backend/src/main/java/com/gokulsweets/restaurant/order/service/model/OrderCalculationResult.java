package com.gokulsweets.restaurant.order.service.model;

import java.math.BigDecimal;
import java.util.List;

/** Immutable order calculation result data contract. */
public record OrderCalculationResult(
        List<CalculatedOrderItem> items,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal priorityCharge,
        BigDecimal convenienceFee,
        BigDecimal convenienceFeeTax,
        BigDecimal totalAmount,
        long feeConfigurationVersion,
        BigDecimal convenienceFeeTaxRate,
        BigDecimal paymentFee,
        BigDecimal paymentFeeTax,
        BigDecimal paymentFeeRate,
        BigDecimal paymentFeeTaxRate) {

    public OrderCalculationResult(
            List<CalculatedOrderItem> items,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal priorityCharge,
            BigDecimal convenienceFee,
            BigDecimal convenienceFeeTax,
            BigDecimal totalAmount,
            long feeConfigurationVersion,
            BigDecimal convenienceFeeTaxRate) {
        this(
                items,
                subtotal,
                taxAmount,
                priorityCharge,
                convenienceFee,
                convenienceFeeTax,
                totalAmount,
                feeConfigurationVersion,
                convenienceFeeTaxRate,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    public OrderCalculationResult(
            List<CalculatedOrderItem> items,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal priorityCharge,
            BigDecimal convenienceFee,
            BigDecimal convenienceFeeTax,
            BigDecimal totalAmount,
            long feeConfigurationVersion) {
        this(
                items,
                subtotal,
                taxAmount,
                priorityCharge,
                convenienceFee,
                convenienceFeeTax,
                totalAmount,
                feeConfigurationVersion,
                BigDecimal.ZERO);
    }

    public OrderCalculationResult(
            List<CalculatedOrderItem> items,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal priorityCharge,
            BigDecimal convenienceFee,
            BigDecimal convenienceFeeTax,
            BigDecimal totalAmount) {
        this(
                items,
                subtotal,
                taxAmount,
                priorityCharge,
                convenienceFee,
                convenienceFeeTax,
                totalAmount,
                0);
    }

    public OrderCalculationResult(
            List<CalculatedOrderItem> items,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal priorityCharge,
            BigDecimal totalAmount) {
        this(
                items,
                subtotal,
                taxAmount,
                priorityCharge,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                totalAmount);
    }
}
