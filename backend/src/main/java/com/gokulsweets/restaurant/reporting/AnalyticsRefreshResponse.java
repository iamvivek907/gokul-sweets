package com.gokulsweets.restaurant.reporting;

public record AnalyticsRefreshResponse(

        int salesDailyRows,

        int branchDailyRows,

        int salesHourlyRows,

        int productDailyRows,

        int customerMetricRows,

        long durationMs
) {
}
