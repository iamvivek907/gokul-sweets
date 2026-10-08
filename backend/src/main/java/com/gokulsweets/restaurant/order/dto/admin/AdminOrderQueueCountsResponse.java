package com.gokulsweets.restaurant.order.dto.admin;

import java.time.LocalDateTime;

/**
 * Immutable admin order queue counts response data contract.
 *
 * @param overdue the overdue
 * @param eligible the eligible
 * @param scheduled the scheduled
 * @param preparing the preparing
 * @param ready the ready
 * @param confirmedTotal the confirmed total
 * @param actionableTotal the actionable total
 * @param generatedAt the generated at
 */
public record AdminOrderQueueCountsResponse(
        long overdue,
        long eligible,
        long scheduled,
        long preparing,
        long ready,
        long confirmedTotal,
        long actionableTotal,
        LocalDateTime generatedAt) {}
