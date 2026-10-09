package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable basket analysis response data contract.
 *
 * @param fromDate the from date
 * @param toDate the to date
 * @param branchId the branch id
 * @param summary the summary
 * @param pairs the pairs
 */
public record BasketAnalysisResponse(
        LocalDate fromDate,
        LocalDate toDate,
        Long branchId,
        BasketAnalysisSummaryResponse summary,
        List<BasketPairResponse> pairs) {}
