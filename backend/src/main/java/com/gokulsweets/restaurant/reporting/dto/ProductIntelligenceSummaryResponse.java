package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/**
 * Immutable product intelligence summary response data contract.
 *
 * @param productsWithSales the products with sales
 * @param totalQuantitySold the total quantity sold
 * @param grossItemRevenue the gross item revenue
 * @param starProducts the star products
 * @param growingProducts the growing products
 * @param decliningProducts the declining products
 */
public record ProductIntelligenceSummaryResponse(
        long productsWithSales,
        long totalQuantitySold,
        BigDecimal grossItemRevenue,
        long starProducts,
        long growingProducts,
        long decliningProducts) {}
