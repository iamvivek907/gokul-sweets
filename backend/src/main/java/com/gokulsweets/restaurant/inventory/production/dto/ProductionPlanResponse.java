package com.gokulsweets.restaurant.inventory.production.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProductionPlanResponse(
        Long branchId,
        LocalDate serviceDate,
        LocalDateTime generatedAt,
        ProductionPlanSummaryResponse summary,
        List<ProductionPlanItemResponse> items
) {
}
