package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

public record SalesWeekdayResponse(

        int isoDayOfWeek,

        String weekday,

        long completedOrders,

        long unitsSold,

        BigDecimal revenue,

        BigDecimal averageRevenuePerActiveDay
) {
}
