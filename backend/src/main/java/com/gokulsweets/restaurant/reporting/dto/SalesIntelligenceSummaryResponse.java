package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/** Immutable sales intelligence summary response data contract. */
public record SalesIntelligenceSummaryResponse(
        BigDecimal revenue,
        long completedOrders,
        long unitsSold,
        long uniqueCustomers,
        BigDecimal averageOrderValue,
        BigDecimal discountAmount,
        BigDecimal previousRevenue,
        long previousCompletedOrders,
        BigDecimal revenueChangePercent,
        BigDecimal ordersChangePercent) {}
