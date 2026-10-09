package com.gokulsweets.restaurant.reporting.dto;

import com.gokulsweets.restaurant.reporting.BasketPairStrength;

import java.math.BigDecimal;

/**
 * Immutable basket pair response data contract.
 *
 * @param productAId the product aid
 * @param productACode the product acode
 * @param productAName the product aname
 * @param productBId the product bid
 * @param productBCode the product bcode
 * @param productBName the product bname
 * @param pairOrderCount the pair order count
 * @param productAOrderCount the product aorder count
 * @param productBOrderCount the product border count
 * @param totalCompletedOrders the total completed orders
 * @param supportPercent the support percent
 * @param confidenceAToBPercent the confidence ato bpercent
 * @param confidenceBToAPercent the confidence bto apercent
 * @param lift the lift
 * @param strength the strength
 * @param explanation the explanation
 */
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
        String explanation) {}
