package com.gokulsweets.restaurant.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable sales daily point response data contract. */
public record SalesDailyPointResponse(
        LocalDate date, BigDecimal revenue, long orders, long unitsSold, long uniqueCustomers) {}
