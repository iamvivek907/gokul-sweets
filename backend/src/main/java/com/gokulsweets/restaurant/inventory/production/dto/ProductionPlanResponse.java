package com.gokulsweets.restaurant.inventory.production.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Immutable production plan response data contract.
 *
 * @param branchId the branch id
 * @param serviceDate the service date
 * @param generatedAt the generated at
 * @param summary the summary
 * @param items the items
 */
public record ProductionPlanResponse(
        Long branchId,
        LocalDate serviceDate,
        LocalDateTime generatedAt,
        ProductionPlanSummaryResponse summary,
        List<ProductionPlanItemResponse> items) {}
