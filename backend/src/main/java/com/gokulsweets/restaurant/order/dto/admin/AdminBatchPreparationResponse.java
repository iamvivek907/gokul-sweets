package com.gokulsweets.restaurant.order.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Immutable admin batch preparation response data contract.
 *
 * @param requested the requested
 * @param attempted the attempted
 * @param started the started
 * @param skipped the skipped
 * @param results the results
 * @param generatedAt the generated at
 */
public record AdminBatchPreparationResponse(
        int requested,
        int attempted,
        int started,
        int skipped,
        List<AdminBatchPreparationItemResponse> results,
        LocalDateTime generatedAt) {}
