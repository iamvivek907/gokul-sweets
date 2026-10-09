package com.gokulsweets.restaurant.inventory.automation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable automation run response data contract.
 *
 * @param id the id
 * @param branchId the branch id
 * @param fromDate the from date
 * @param throughDate the through date
 * @param triggerType the trigger type
 * @param status the status
 * @param createdCount the created count
 * @param updatedCount the updated count
 * @param suggestedCount the suggested count
 * @param skippedCount the skipped count
 * @param errorCount the error count
 * @param initiatedBy the initiated by
 * @param startedAt the started at
 * @param completedAt the completed at
 * @param errorSummary the error summary
 */
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
        String errorSummary) {}
