package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable demand forecast response data contract.
 *
 * @param targetDate the target date
 * @param branchId the branch id
 * @param branchName the branch name
 * @param historyWeeks the history weeks
 * @param summary the summary
 * @param products the products
 */
public record DemandForecastResponse(
        LocalDate targetDate,
        Long branchId,
        String branchName,
        int historyWeeks,
        DemandForecastSummaryResponse summary,
        List<DemandForecastItemResponse> products) {}
