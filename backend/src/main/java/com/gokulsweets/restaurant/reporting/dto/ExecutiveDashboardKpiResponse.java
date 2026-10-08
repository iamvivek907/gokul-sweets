package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;

/** Immutable executive dashboard kpi response data contract. */
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
