package com.gokulsweets.restaurant.inventory.automation.dto;

import java.util.List;

/**
 * Immutable automation workspace response data contract.
 *
 * @param branchId the branch id
 * @param rules the rules
 * @param recentRuns the recent runs
 */
public record AutomationWorkspaceResponse(
        Long branchId,
        List<AutomationRuleResponse> rules,
        List<AutomationRunResponse> recentRuns) {}
