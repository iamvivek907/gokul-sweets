package com.gokulsweets.restaurant.staff.leave.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveRequestResponse(

        Long id,

        String approvalRequestNumber,

        ApprovalRequestStatus status,

        Integer workflowVersion,

        Long branchId,

        String branchCode,

        String branchName,

        LocalDate startDate,

        LocalDate endDate,

        long totalDays,

        String reason,

        LocalDateTime submittedAt,

        LocalDateTime resolvedAt,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {
}
