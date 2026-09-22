package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

public record ExecutiveDashboardResponse(

        LocalDate fromDate,

        LocalDate toDate,

        LocalDate comparisonFromDate,

        LocalDate comparisonToDate,

        Long branchId,

        ExecutiveDashboardKpiResponse kpis,

        List<ExecutiveDashboardTrendPointResponse> revenueTrend,

        List<ExecutiveDashboardBranchResponse> branches,

        List<ExecutiveDashboardHourlyResponse> hourlyDemand,

        ExecutiveDashboardHighlightResponse highlights
) {
}
