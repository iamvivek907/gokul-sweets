package com.gokulsweets.restaurant.reporting.dto;

/** Immutable business insights summary response data contract. */
public record BusinessInsightsSummaryResponse(
        long totalInsights, long actionInsights, long watchInsights, long infoInsights) {}
