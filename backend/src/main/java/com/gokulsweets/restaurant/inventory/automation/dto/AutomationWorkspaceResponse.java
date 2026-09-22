package com.gokulsweets.restaurant.inventory.automation.dto;

import java.util.List;

public record AutomationWorkspaceResponse(
        Long branchId,
        List<AutomationRuleResponse> rules,
        List<AutomationRunResponse> recentRuns
) {
}
