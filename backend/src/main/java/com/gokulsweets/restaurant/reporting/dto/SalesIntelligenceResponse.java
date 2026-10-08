package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/** Immutable sales intelligence response data contract. */
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
