package com.gokulsweets.restaurant.reporting.dto;

import com.gokulsweets.restaurant.reporting.DemandForecastConfidence;

import java.math.BigDecimal;

/**
 * Immutable demand forecast item response data contract.
 *
 * @param productId the product id
 * @param productCode the product code
 * @param productName the product name
 * @param categoryId the category id
 * @param categoryCode the category code
 * @param categoryName the category name
 * @param recommendedQuantity the recommended quantity
 * @param lowerQuantity the lower quantity
 * @param upperQuantity the upper quantity
 * @param recentFourWeekAverage the recent four week average
 * @param previousEightWeekAverage the previous eight week average
 * @param trendPercent the trend percent
 * @param variabilityPercent the variability percent
 * @param weeksObserved the weeks observed
 * @param weeksWithSales the weeks with sales
 * @param confidence the confidence
 * @param explanation the explanation
 */
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
        String explanation) {}
