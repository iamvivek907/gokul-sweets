package com.gokulsweets.restaurant.staff.attendance.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.attendance.AttendanceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AttendanceResponse(

        Long id,

        String approvalRequestNumber,

        ApprovalRequestStatus status,

        Integer workflowVersion,

        Long branchId,

        String branchCode,

        String branchName,

        LocalDate attendanceDate,

        AttendanceType attendanceType,

        LocalTime checkInTime,

        LocalTime checkOutTime,

        String note,

        LocalDateTime submittedAt,

        LocalDateTime resolvedAt,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {
}
