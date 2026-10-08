package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/** Immutable sales pickup hour response data contract. */
public record SalesPickupHourResponse(
        int hour, long completedOrders, long unitsSold, BigDecimal revenue) {}
