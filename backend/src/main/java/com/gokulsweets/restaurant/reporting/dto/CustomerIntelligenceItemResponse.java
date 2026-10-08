package com.gokulsweets.restaurant.reporting.dto;

import com.gokulsweets.restaurant.reporting.CustomerLifecycleState;
import com.gokulsweets.restaurant.reporting.CustomerValueSegment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable customer intelligence item response data contract.
 *
 * @param customerId the customer id
 * @param latestName the latest name
 * @param normalizedPhone the normalized phone
 * @param verificationStatus the verification status
 * @param firstPurchaseAt the first purchase at
 * @param lastPurchaseAt the last purchase at
 * @param lifetimeOrders the lifetime orders
 * @param lifetimeSpend the lifetime spend
 * @param averageOrderValue the average order value
 * @param orders30d the orders30d
 * @param orders90d the orders90d
 * @param orders365d the orders365d
 * @param spend30d the spend30d
 * @param spend90d the spend90d
 * @param spend365d the spend365d
 * @param daysSinceLastPurchase the days since last purchase
 * @param expectedPurchaseGapDays the expected purchase gap days
 * @param lastPurchaseGapDays the last purchase gap days
 * @param currentGapRatio the current gap ratio
 * @param lifecycleState the lifecycle state
 * @param valueSegment the value segment
 * @param lifecycleReason the lifecycle reason
 */
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
        String lifecycleReason) {}
