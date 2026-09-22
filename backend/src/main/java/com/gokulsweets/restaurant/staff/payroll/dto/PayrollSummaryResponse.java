package com.gokulsweets.restaurant.staff.payroll.dto;

import java.math.BigDecimal;

public record PayrollSummaryResponse(

        Long staffUserId,

        String staffName,

        BigDecimal totalEarned,

        BigDecimal committedPayments,

        BigDecimal availableToRequest
) {
}
