package com.gokulsweets.restaurant.inventory.automation.service;

import com.gokulsweets.restaurant.inventory.automation.dto.AutomationRunResponse;
import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRun;

final class InventoryAutomationMapper {
    private InventoryAutomationMapper() {
    }

    static AutomationRunResponse toRunResponse(InventoryAutomationRun run) {
        return new AutomationRunResponse(
                run.getId(), run.getBranch().getId(), run.getFromDate(), run.getThroughDate(),
                run.getTriggerType().name(), run.getRunStatus().name(),
                run.getCreatedCount(), run.getUpdatedCount(), run.getSuggestedCount(),
                run.getSkippedCount(), run.getErrorCount(), run.getInitiatedBy(),
                run.getStartedAt(), run.getCompletedAt(), run.getErrorSummary()
        );
    }
}
