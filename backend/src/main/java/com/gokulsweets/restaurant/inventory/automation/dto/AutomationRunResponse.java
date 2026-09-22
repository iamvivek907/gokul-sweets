package com.gokulsweets.restaurant.inventory.automation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AutomationRunResponse(
        Long id,
        Long branchId,
        LocalDate fromDate,
        LocalDate throughDate,
        String triggerType,
        String status,
        int createdCount,
        int updatedCount,
        int suggestedCount,
        int skippedCount,
        int errorCount,
        String initiatedBy,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorSummary
) {
}
