package com.gokulsweets.restaurant.customer.dto;

import com.gokulsweets.restaurant.customer.CustomerContactStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerDetailResponse(

        Long id,

        String latestName,

        String normalizedPhone,

        CustomerContactStatus verificationStatus,

        LocalDateTime firstSeenAt,

        LocalDateTime lastSeenAt,

        long orderCount,

        long completedPurchaseCount,

        BigDecimal lifetimeSpend,

        BigDecimal averageOrderValue,

        LocalDateTime lastPurchaseAt
) {
}
