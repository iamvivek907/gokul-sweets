package com.gokulsweets.restaurant.staff.payroll.dto;

import com.gokulsweets.restaurant.staff.attendance.AttendanceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EarningResponse(

        Long id,

        Long attendanceId,

        Long branchId,

        String branchName,

        LocalDate earningDate,

        AttendanceType attendanceType,

        BigDecimal rateSnapshot,

        BigDecimal amount,

        LocalDateTime createdAt
) {
}
