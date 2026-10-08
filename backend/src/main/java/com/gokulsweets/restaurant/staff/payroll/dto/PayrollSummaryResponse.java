package com.gokulsweets.restaurant.staff.payroll.dto;

import java.math.BigDecimal;

/** Immutable payroll summary response data contract. */
public record PayrollSummaryResponse(
        Long staffUserId,
        String staffName,
        BigDecimal totalEarned,
        BigDecimal committedPayments,
        BigDecimal availableToRequest) {}
