package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

public record ExecutiveDashboardHourlyResponse(

        int hour,

        long orders,

        long unitsSold,

        BigDecimal revenue
) {
}
