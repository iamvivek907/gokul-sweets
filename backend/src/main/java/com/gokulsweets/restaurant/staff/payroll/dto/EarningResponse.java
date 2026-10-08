package com.gokulsweets.restaurant.staff.payroll.dto;

import com.gokulsweets.restaurant.staff.attendance.AttendanceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable earning response data contract.
 *
 * @param id the id
 * @param attendanceId the attendance id
 * @param branchId the branch id
 * @param branchName the branch name
 * @param earningDate the earning date
 * @param attendanceType the attendance type
 * @param rateSnapshot the rate snapshot
 * @param amount the amount
 * @param createdAt the created at
 */
public record EarningResponse(
        Long id,
        Long attendanceId,
        Long branchId,
        String branchName,
        LocalDate earningDate,
        AttendanceType attendanceType,
        BigDecimal rateSnapshot,
        BigDecimal amount,
        LocalDateTime createdAt) {}
