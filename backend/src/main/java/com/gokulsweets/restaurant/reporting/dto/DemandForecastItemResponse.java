package com.gokulsweets.restaurant.reporting.dto;

import com.gokulsweets.restaurant.reporting.DemandForecastConfidence;

import java.math.BigDecimal;

public record DemandForecastItemResponse(

        Long productId,

        String productCode,

        String productName,

        Long categoryId,

        String categoryCode,

        String categoryName,

        int recommendedQuantity,

        int lowerQuantity,

        int upperQuantity,

        BigDecimal recentFourWeekAverage,

        BigDecimal previousEightWeekAverage,

        BigDecimal trendPercent,

        BigDecimal variabilityPercent,

        int weeksObserved,

        int weeksWithSales,

        DemandForecastConfidence confidence,

        String explanation
) {
}
