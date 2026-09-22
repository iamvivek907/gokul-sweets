package com.gokulsweets.restaurant.reporting.dto;

import com.gokulsweets.restaurant.reporting.ProductPerformanceState;

import java.math.BigDecimal;

public record ProductIntelligenceItemResponse(

        Long productId,

        String productCode,

        String productName,

        Long categoryId,

        String categoryCode,

        String categoryName,

        long orderCount,

        long quantitySold,

        BigDecimal grossItemRevenue,

        long uniqueCustomers,

        int activeSalesDays,

        BigDecimal orderPenetrationPercent,

        BigDecimal activeDayConsistencyPercent,

        BigDecimal previousRevenue,

        long previousQuantitySold,

        BigDecimal revenueGrowthPercent,

        ProductPerformanceState state,

        String stateReason
) {
}
