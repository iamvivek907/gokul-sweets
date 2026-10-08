package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/** Immutable executive dashboard response data contract. */
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
        ExecutiveDashboardHighlightResponse highlights) {}
