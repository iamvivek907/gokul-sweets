package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable executive dashboard trend point response data contract.
 *
 * @param date the date
 * @param revenue the revenue
 * @param orders the orders
 * @param unitsSold the units sold
 * @param uniqueCustomers the unique customers
 */
public record ExecutiveDashboardTrendPointResponse(
        LocalDate date, BigDecimal revenue, long orders, long unitsSold, long uniqueCustomers) {}
