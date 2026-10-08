package com.gokulsweets.restaurant.order.dto.admin;

import java.time.LocalDateTime;

/** Immutable admin order queue counts response data contract. */
public record AdminOrderQueueCountsResponse(
        long overdue,
        long eligible,
        long scheduled,
        long preparing,
        long ready,
        long confirmedTotal,
        long actionableTotal,
        LocalDateTime generatedAt) {}
