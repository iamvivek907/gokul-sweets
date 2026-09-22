package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

public record ProductIntelligenceSummaryResponse(

        long productsWithSales,

        long totalQuantitySold,

        BigDecimal grossItemRevenue,

        long starProducts,

        long growingProducts,

        long decliningProducts
) {
}
