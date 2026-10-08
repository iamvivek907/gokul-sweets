package com.gokulsweets.restaurant.reporting.dto;

import com.gokulsweets.restaurant.reporting.ProductPerformanceState;

import java.math.BigDecimal;

/**
 * Immutable product intelligence item response data contract.
 *
 * @param productId the product id
 * @param productCode the product code
 * @param productName the product name
 * @param categoryId the category id
 * @param categoryCode the category code
 * @param categoryName the category name
 * @param orderCount the order count
 * @param quantitySold the quantity sold
 * @param grossItemRevenue the gross item revenue
 * @param uniqueCustomers the unique customers
 * @param activeSalesDays the active sales days
 * @param orderPenetrationPercent the order penetration percent
 * @param activeDayConsistencyPercent the active day consistency percent
 * @param previousRevenue the previous revenue
 * @param previousQuantitySold the previous quantity sold
 * @param revenueGrowthPercent the revenue growth percent
 * @param state the state
 * @param stateReason the state reason
 */
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
        String stateReason) {}
