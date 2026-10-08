package com.gokulsweets.restaurant.order.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Immutable admin order queue response data contract.
 *
 * @param orders the orders
 * @param returnedCount the returned count
 * @param limit the limit
 * @param hasMore the has more
 * @param generatedAt the generated at
 */
public record AdminOrderQueueResponse(
        List<AdminOrderQueueItemResponse> orders,
        int returnedCount,
        int limit,
        boolean hasMore,
        LocalDateTime generatedAt) {}
