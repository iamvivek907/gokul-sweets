package com.gokulsweets.restaurant.staff.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable payroll opening balance response data contract.
 *
 * @param id the id
 * @param staffUserId the staff user id
 * @param asOfDate the as of date
 * @param earnedAmount the earned amount
 * @param takenAmount the taken amount
 * @param netOpeningBalance the net opening balance
 * @param note the note
 * @param createdByName the created by name
 * @param createdAt the created at
 */
public record PayrollOpeningBalanceResponse(
        Long id,
        Long staffUserId,
        LocalDate asOfDate,
        BigDecimal earnedAmount,
        BigDecimal takenAmount,
        BigDecimal netOpeningBalance,
        String note,
        String createdByName,
        LocalDateTime createdAt) {}
