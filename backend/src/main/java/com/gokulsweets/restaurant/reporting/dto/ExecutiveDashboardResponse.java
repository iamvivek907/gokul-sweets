package com.gokulsweets.restaurant.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable executive dashboard response data contract.
 *
 * @param fromDate the from date
 * @param toDate the to date
 * @param comparisonFromDate the comparison from date
 * @param comparisonToDate the comparison to date
 * @param branchId the branch id
 * @param kpis the kpis
 * @param revenueTrend the revenue trend
 * @param branches the branches
 * @param hourlyDemand the hourly demand
 * @param highlights the highlights
 */
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
