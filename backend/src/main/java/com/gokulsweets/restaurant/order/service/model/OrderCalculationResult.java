package com.gokulsweets.restaurant.order.service.model;

import java.math.BigDecimal;
import java.util.List;

public record OrderCalculationResult(
        List<CalculatedOrderItem> items,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal priorityCharge,
        BigDecimal convenienceFee,
        BigDecimal convenienceFeeTax,
        BigDecimal totalAmount,
        long feeConfigurationVersion,
        BigDecimal convenienceFeeTaxRate
) {
    public OrderCalculationResult(List<CalculatedOrderItem> items,BigDecimal subtotal,BigDecimal taxAmount,BigDecimal priorityCharge,BigDecimal convenienceFee,BigDecimal convenienceFeeTax,BigDecimal totalAmount,long feeConfigurationVersion) {
        this(items,subtotal,taxAmount,priorityCharge,convenienceFee,convenienceFeeTax,totalAmount,feeConfigurationVersion,BigDecimal.ZERO);
    }
    public OrderCalculationResult(List<CalculatedOrderItem> items,BigDecimal subtotal,BigDecimal taxAmount,BigDecimal priorityCharge,BigDecimal convenienceFee,BigDecimal convenienceFeeTax,BigDecimal totalAmount) {
        this(items,subtotal,taxAmount,priorityCharge,convenienceFee,convenienceFeeTax,totalAmount,0);
    }
    public OrderCalculationResult(List<CalculatedOrderItem> items,BigDecimal subtotal,BigDecimal taxAmount,BigDecimal priorityCharge,BigDecimal totalAmount) {
        this(items,subtotal,taxAmount,priorityCharge,BigDecimal.ZERO,BigDecimal.ZERO,totalAmount);
    }
}
