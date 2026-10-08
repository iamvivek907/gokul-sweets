package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/** Immutable product intelligence summary response data contract. */
public record ProductIntelligenceSummaryResponse(
        long productsWithSales,
        long totalQuantitySold,
        BigDecimal grossItemRevenue,
        long starProducts,
        long growingProducts,
        long decliningProducts) {}
