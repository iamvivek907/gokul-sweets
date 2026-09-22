package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

public record SalesPickupHourResponse(

        int hour,

        long completedOrders,

        long unitsSold,

        BigDecimal revenue
) {
}
