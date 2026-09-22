package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

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

        BigDecimal ordersChangePercent
) {
}
