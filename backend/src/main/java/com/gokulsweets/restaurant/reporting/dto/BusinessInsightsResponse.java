package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable business insights response data contract.
 *
 * @param fromDate the from date
 * @param toDate the to date
 * @param branchId the branch id
 * @param summary the summary
 * @param insights the insights
 */
public record BusinessInsightsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Long branchId,
        BusinessInsightsSummaryResponse summary,
        List<BusinessInsightResponse> insights) {}
