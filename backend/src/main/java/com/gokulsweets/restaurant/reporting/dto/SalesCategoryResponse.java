package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

public record SalesCategoryResponse(

        Long categoryId,

        String categoryCode,

        String categoryName,

        long orderCount,

        long quantitySold,

        BigDecimal grossItemRevenue,

        BigDecimal revenueSharePercent
) {
}
