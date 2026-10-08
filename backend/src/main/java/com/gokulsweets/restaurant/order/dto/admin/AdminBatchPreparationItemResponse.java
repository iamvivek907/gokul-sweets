package com.gokulsweets.restaurant.order.dto.admin;

import com.gokulsweets.restaurant.order.enums.PreparationBatchResult;

/** Immutable admin batch preparation item response data contract. */
public record AdminBatchPreparationItemResponse(
        String orderNumber, PreparationBatchResult result, String message) {}
