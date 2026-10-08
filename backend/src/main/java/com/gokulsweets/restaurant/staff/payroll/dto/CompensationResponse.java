package com.gokulsweets.restaurant.staff.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Immutable compensation response data contract. */
public record CompensationResponse(
        Long id,
        Long staffUserId,
        LocalDate effectiveFrom,
        BigDecimal dailyRate,
        BigDecimal halfDayRate,
        String createdByName,
        LocalDateTime createdAt) {}
