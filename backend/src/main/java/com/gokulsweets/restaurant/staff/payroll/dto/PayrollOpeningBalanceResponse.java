package com.gokulsweets.restaurant.staff.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PayrollOpeningBalanceResponse(

        Long id,

        Long staffUserId,

        LocalDate asOfDate,

        BigDecimal earnedAmount,

        BigDecimal takenAmount,

        BigDecimal netOpeningBalance,

        String note,

        String createdByName,

        LocalDateTime createdAt
) {
}
