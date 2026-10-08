package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/** Immutable executive dashboard hourly response data contract. */
public record ExecutiveDashboardHourlyResponse(
        int hour, long orders, long unitsSold, BigDecimal revenue) {}
