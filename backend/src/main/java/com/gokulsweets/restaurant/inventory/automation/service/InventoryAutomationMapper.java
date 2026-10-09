package com.gokulsweets.restaurant.inventory.automation.service;

import com.gokulsweets.restaurant.inventory.automation.dto.AutomationRunResponse;
import com.gokulsweets.restaurant.inventory.automation.entity.InventoryAutomationRun;
import com.gokulsweets.restaurant.observability.MethodTiming;

/** Backend inventory automation mapper contract and implementation. */
final class InventoryAutomationMapper {

    /** Creates a inventory automation mapper instance. */
    private InventoryAutomationMapper() {}

    /**
     * Tos run response.
     *
     * @param run the run
     * @return the to run response result
     */
    static AutomationRunResponse toRunResponse(InventoryAutomationRun run) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        InventoryAutomationMapper.class, "toRunResponse(InventoryAutomationRun)");
        try {
            return new AutomationRunResponse(
                    run.getId(),
                    run.getBranch().getId(),
                    run.getFromDate(),
                    run.getThroughDate(),
                    run.getTriggerType().name(),
                    run.getRunStatus().name(),
                    run.getCreatedCount(),
                    run.getUpdatedCount(),
                    run.getSuggestedCount(),
                    run.getSkippedCount(),
                    run.getErrorCount(),
                    run.getInitiatedBy(),
                    run.getStartedAt(),
                    run.getCompletedAt(),
                    run.getErrorSummary());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryAutomationMapper.class,
                    "toRunResponse(InventoryAutomationRun)");
        }
    }
}
