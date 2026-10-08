package com.gokulsweets.restaurant.staff.attendance.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.attendance.AttendanceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Immutable attendance response data contract.
 *
 * @param id the id
 * @param approvalRequestNumber the approval request number
 * @param status the status
 * @param workflowVersion the workflow version
 * @param branchId the branch id
 * @param branchCode the branch code
 * @param branchName the branch name
 * @param attendanceDate the attendance date
 * @param attendanceType the attendance type
 * @param checkInTime the check in time
 * @param checkOutTime the check out time
 * @param note the note
 * @param submittedAt the submitted at
 * @param resolvedAt the resolved at
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
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
        LocalDateTime updatedAt) {}
