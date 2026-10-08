package com.gokulsweets.restaurant.staff.payroll.dto;

import java.math.BigDecimal;

/**
 * Immutable payroll summary response data contract.
 *
 * @param staffUserId the staff user id
 * @param staffName the staff name
 * @param totalEarned the total earned
 * @param committedPayments the committed payments
 * @param availableToRequest the available to request
 */
public record PayrollSummaryResponse(
        Long staffUserId,
        String staffName,
        BigDecimal totalEarned,
        BigDecimal committedPayments,
        BigDecimal availableToRequest) {}
