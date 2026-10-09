package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/**
 * Immutable sales pickup hour response data contract.
 *
 * @param hour the hour
 * @param completedOrders the completed orders
 * @param unitsSold the units sold
 * @param revenue the revenue
 */
public record SalesPickupHourResponse(
        int hour, long completedOrders, long unitsSold, BigDecimal revenue) {}
