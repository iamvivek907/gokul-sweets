package com.gokulsweets.restaurant.staff.leave.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable leave request response data contract.
 *
 * @param id the id
 * @param approvalRequestNumber the approval request number
 * @param status the status
 * @param workflowVersion the workflow version
 * @param branchId the branch id
 * @param branchCode the branch code
 * @param branchName the branch name
 * @param startDate the start date
 * @param endDate the end date
 * @param totalDays the total days
 * @param reason the reason
 * @param submittedAt the submitted at
 * @param resolvedAt the resolved at
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
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
        LocalDateTime updatedAt) {}
