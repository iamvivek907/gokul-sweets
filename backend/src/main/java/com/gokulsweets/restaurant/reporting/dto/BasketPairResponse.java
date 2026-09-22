package com.gokulsweets.restaurant.reporting.dto;

import com.gokulsweets.restaurant.reporting.BasketPairStrength;

import java.math.BigDecimal;

public record BasketPairResponse(

        Long productAId,

        String productACode,

        String productAName,

        Long productBId,

        String productBCode,

        String productBName,

        long pairOrderCount,

        long productAOrderCount,

        long productBOrderCount,

        long totalCompletedOrders,

        BigDecimal supportPercent,

        BigDecimal confidenceAToBPercent,

        BigDecimal confidenceBToAPercent,

        BigDecimal lift,

        BasketPairStrength strength,

        String explanation
) {
}
