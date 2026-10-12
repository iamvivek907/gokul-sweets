package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/**
 * Immutable sales intelligence summary response data contract.
 *
 * @param revenue the revenue
 * @param completedOrders the completed orders
 * @param unitsSold the units sold
 * @param uniqueCustomers the unique customers
 * @param averageOrderValue the average order value
 * @param discountAmount the discount amount
 * @param previousRevenue the previous revenue
 * @param previousCompletedOrders the previous completed orders
 * @param revenueChangePercent the revenue change percent
 * @param ordersChangePercent the orders change percent
 */
public record SalesIntelligenceSummaryResponse(
        BigDecimal revenue,
        long completedOrders,
        long unitsSold,
        long uniqueCustomers,
        BigDecimal averageOrderValue,
        BigDecimal discountAmount,
        BigDecimal previousRevenue,
        long previousCompletedOrders,
        BigDecimal revenueChangePercent,
        BigDecimal ordersChangePercent) {}
