package com.gokulsweets.restaurant.order.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

/** Immutable admin order queue response data contract. */
public record AdminOrderQueueResponse(
        List<AdminOrderQueueItemResponse> orders,
        int returnedCount,
        int limit,
        boolean hasMore,
        LocalDateTime generatedAt) {}
