package com.gokulsweets.restaurant.inventory.automation.dto;

import java.util.List;

/** Immutable automation workspace response data contract. */
public record AutomationWorkspaceResponse(
        Long branchId,
        List<AutomationRuleResponse> rules,
        List<AutomationRunResponse> recentRuns) {}
