package com.gokulsweets.restaurant.reporting.dto;

import com.gokulsweets.restaurant.reporting.CustomerLifecycleState;
import com.gokulsweets.restaurant.reporting.CustomerValueSegment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerIntelligenceItemResponse(

        Long customerId,

        String latestName,

        String normalizedPhone,

        String verificationStatus,

        LocalDateTime firstPurchaseAt,

        LocalDateTime lastPurchaseAt,

        long lifetimeOrders,

        BigDecimal lifetimeSpend,

        BigDecimal averageOrderValue,

        long orders30d,

        long orders90d,

        long orders365d,

        BigDecimal spend30d,

        BigDecimal spend90d,

        BigDecimal spend365d,

        Integer daysSinceLastPurchase,

        BigDecimal expectedPurchaseGapDays,

        Integer lastPurchaseGapDays,

        BigDecimal currentGapRatio,

        CustomerLifecycleState lifecycleState,

        CustomerValueSegment valueSegment,

        String lifecycleReason
) {
}
