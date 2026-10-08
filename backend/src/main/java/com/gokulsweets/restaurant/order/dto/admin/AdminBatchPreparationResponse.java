package com.gokulsweets.restaurant.order.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

/** Immutable admin batch preparation response data contract. */
public record AdminBatchPreparationResponse(
        int requested,
        int attempted,
        int started,
        int skipped,
        List<AdminBatchPreparationItemResponse> results,
        LocalDateTime generatedAt) {}
