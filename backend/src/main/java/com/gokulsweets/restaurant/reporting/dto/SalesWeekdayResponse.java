package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/**
 * Immutable sales weekday response data contract.
 *
 * @param isoDayOfWeek the iso day of week
 * @param weekday the weekday
 * @param completedOrders the completed orders
 * @param unitsSold the units sold
 * @param revenue the revenue
 * @param averageRevenuePerActiveDay the average revenue per active day
 */
public record SalesWeekdayResponse(
        int isoDayOfWeek,
        String weekday,
        long completedOrders,
        long unitsSold,
        BigDecimal revenue,
        BigDecimal averageRevenuePerActiveDay) {}
