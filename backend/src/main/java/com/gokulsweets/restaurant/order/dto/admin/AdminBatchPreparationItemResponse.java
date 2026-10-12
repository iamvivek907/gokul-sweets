package com.gokulsweets.restaurant.order.dto.admin;

import com.gokulsweets.restaurant.order.enums.PreparationBatchResult;

/**
 * Immutable admin batch preparation item response data contract.
 *
 * @param orderNumber the order number
 * @param result the result
 * @param message the message
 */
public record AdminBatchPreparationItemResponse(
        String orderNumber, PreparationBatchResult result, String message) {}
