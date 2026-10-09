package com.gokulsweets.restaurant.reporting.dto;

/**
 * Immutable business insights summary response data contract.
 *
 * @param totalInsights the total insights
 * @param actionInsights the action insights
 * @param watchInsights the watch insights
 * @param infoInsights the info insights
 */
public record BusinessInsightsSummaryResponse(
        long totalInsights, long actionInsights, long watchInsights, long infoInsights) {}
