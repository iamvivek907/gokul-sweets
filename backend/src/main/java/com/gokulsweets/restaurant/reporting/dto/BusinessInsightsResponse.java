package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

public record BusinessInsightsResponse(

        LocalDate fromDate,

        LocalDate toDate,

        Long branchId,

        BusinessInsightsSummaryResponse summary,

        List<BusinessInsightResponse> insights
) {
}
