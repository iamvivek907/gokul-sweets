package com.gokulsweets.restaurant.reporting;

/**
 * Immutable analytics refresh response data contract.
 *
 * @param salesDailyRows the sales daily rows
 * @param branchDailyRows the branch daily rows
 * @param salesHourlyRows the sales hourly rows
 * @param productDailyRows the product daily rows
 * @param customerMetricRows the customer metric rows
 * @param durationMs the duration ms
 */
public record AnalyticsRefreshResponse(
        int salesDailyRows,
        int branchDailyRows,
        int salesHourlyRows,
        int productDailyRows,
        int customerMetricRows,
        long durationMs) {}
