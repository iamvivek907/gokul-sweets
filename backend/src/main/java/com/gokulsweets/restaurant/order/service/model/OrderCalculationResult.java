package com.gokulsweets.restaurant.order.service.model;

import java.math.BigDecimal;
import java.util.List;

public record OrderCalculationResult(
        List<CalculatedOrderItem> items,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal priorityCharge,
        BigDecimal totalAmount
) {
}