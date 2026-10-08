package com.gokulsweets.restaurant.reporting;

/** Immutable analytics refresh response data contract. */
public record AnalyticsRefreshResponse(
        int salesDailyRows,
        int branchDailyRows,
        int salesHourlyRows,
        int productDailyRows,
        int customerMetricRows,
        long durationMs) {}
