package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/**
 * Immutable sales category response data contract.
 *
 * @param categoryId the category id
 * @param categoryCode the category code
 * @param categoryName the category name
 * @param orderCount the order count
 * @param quantitySold the quantity sold
 * @param grossItemRevenue the gross item revenue
 * @param revenueSharePercent the revenue share percent
 */
public record SalesCategoryResponse(
        Long categoryId,
        String categoryCode,
        String categoryName,
        long orderCount,
        long quantitySold,
        BigDecimal grossItemRevenue,
        BigDecimal revenueSharePercent) {}
