package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/**
 * Immutable executive dashboard hourly response data contract.
 *
 * @param hour the hour
 * @param orders the orders
 * @param unitsSold the units sold
 * @param revenue the revenue
 */
public record ExecutiveDashboardHourlyResponse(
        int hour, long orders, long unitsSold, BigDecimal revenue) {}
