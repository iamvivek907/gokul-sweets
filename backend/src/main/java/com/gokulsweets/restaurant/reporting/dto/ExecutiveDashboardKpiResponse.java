package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/**
 * Immutable executive dashboard kpi response data contract.
 *
 * @param revenue the revenue
 * @param completedOrders the completed orders
 * @param unitsSold the units sold
 * @param uniqueCustomers the unique customers
 * @param averageOrderValue the average order value
 * @param discountAmount the discount amount
 * @param revenueChangePercent the revenue change percent
 * @param ordersChangePercent the orders change percent
 * @param customersChangePercent the customers change percent
 */
public record ExecutiveDashboardKpiResponse(
        BigDecimal revenue,
        long completedOrders,
        long unitsSold,
        long uniqueCustomers,
        BigDecimal averageOrderValue,
        BigDecimal discountAmount,
        BigDecimal revenueChangePercent,
        BigDecimal ordersChangePercent,
        BigDecimal customersChangePercent) {}
