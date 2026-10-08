package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable executive dashboard trend point response data contract. */
public record ExecutiveDashboardTrendPointResponse(
        LocalDate date, BigDecimal revenue, long orders, long unitsSold, long uniqueCustomers) {}
