package com.gokulsweets.restaurant.order.service.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable order calculation result data contract.
 *
 * @param items the items
 * @param subtotal the subtotal
 * @param taxAmount the tax amount
 * @param priorityCharge the priority charge
 * @param convenienceFee the convenience fee
 * @param convenienceFeeTax the convenience fee tax
 * @param totalAmount the total amount
 * @param feeConfigurationVersion the fee configuration version
 * @param convenienceFeeTaxRate the convenience fee tax rate
 * @param paymentFee the payment fee
 * @param paymentFeeTax the payment fee tax
 * @param paymentFeeRate the payment fee rate
 * @param paymentFeeTaxRate the payment fee tax rate
 */
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

    /**
     * Creates a order calculation result instance.
     *
     * @param items the items
     * @param subtotal the subtotal
     * @param taxAmount the tax amount
     * @param priorityCharge the priority charge
     * @param convenienceFee the convenience fee
     * @param convenienceFeeTax the convenience fee tax
     * @param totalAmount the total amount
     * @param feeConfigurationVersion the fee configuration version
     * @param convenienceFeeTaxRate the convenience fee tax rate
     */
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

    /**
     * Creates a order calculation result instance.
     *
     * @param items the items
     * @param subtotal the subtotal
     * @param taxAmount the tax amount
     * @param priorityCharge the priority charge
     * @param convenienceFee the convenience fee
     * @param convenienceFeeTax the convenience fee tax
     * @param totalAmount the total amount
     * @param feeConfigurationVersion the fee configuration version
     */
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

    /**
     * Creates a order calculation result instance.
     *
     * @param items the items
     * @param subtotal the subtotal
     * @param taxAmount the tax amount
     * @param priorityCharge the priority charge
     * @param convenienceFee the convenience fee
     * @param convenienceFeeTax the convenience fee tax
     * @param totalAmount the total amount
     */
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

    /**
     * Creates a order calculation result instance.
     *
     * @param items the items
     * @param subtotal the subtotal
     * @param taxAmount the tax amount
     * @param priorityCharge the priority charge
     * @param totalAmount the total amount
     */
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
