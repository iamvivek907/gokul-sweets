package com.gokulsweets.restaurant.staff.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable compensation response data contract.
 *
 * @param id the id
 * @param staffUserId the staff user id
 * @param effectiveFrom the effective from
 * @param dailyRate the daily rate
 * @param halfDayRate the half day rate
 * @param createdByName the created by name
 * @param createdAt the created at
 */
public record CompensationResponse(
        Long id,
        Long staffUserId,
        LocalDate effectiveFrom,
        BigDecimal dailyRate,
        BigDecimal halfDayRate,
        String createdByName,
        LocalDateTime createdAt) {}
