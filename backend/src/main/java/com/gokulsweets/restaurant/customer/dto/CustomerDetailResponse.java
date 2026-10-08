package com.gokulsweets.restaurant.customer.dto;

import com.gokulsweets.restaurant.customer.CustomerContactStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable customer detail response data contract.
 *
 * @param id the id
 * @param latestName the latest name
 * @param normalizedPhone the normalized phone
 * @param verificationStatus the verification status
 * @param firstSeenAt the first seen at
 * @param lastSeenAt the last seen at
 * @param orderCount the order count
 * @param completedPurchaseCount the completed purchase count
 * @param lifetimeSpend the lifetime spend
 * @param averageOrderValue the average order value
 * @param lastPurchaseAt the last purchase at
 */
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
        LocalDateTime lastPurchaseAt) {}
