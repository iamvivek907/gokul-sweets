package com.gokulsweets.restaurant.reporting.dto;

public record BusinessInsightsSummaryResponse(

        long totalInsights,

        long actionInsights,

        long watchInsights,

        long infoInsights
) {
}
