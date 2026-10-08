package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable sales intelligence response data contract.
 *
 * @param fromDate the from date
 * @param toDate the to date
 * @param comparisonFromDate the comparison from date
 * @param comparisonToDate the comparison to date
 * @param branchId the branch id
 * @param summary the summary
 * @param dailyTrend the daily trend
 * @param weekdayPerformance the weekday performance
 * @param categoryContribution the category contribution
 * @param branchMix the branch mix
 * @param pickupHours the pickup hours
 */
public record SalesIntelligenceResponse(
        LocalDate fromDate,
        LocalDate toDate,
        LocalDate comparisonFromDate,
        LocalDate comparisonToDate,
        Long branchId,
        SalesIntelligenceSummaryResponse summary,
        List<SalesDailyPointResponse> dailyTrend,
        List<SalesWeekdayResponse> weekdayPerformance,
        List<SalesCategoryResponse> categoryContribution,
        List<SalesBranchMixResponse> branchMix,
        List<SalesPickupHourResponse> pickupHours) {}
