package com.gokulsweets.restaurant.staff.payroll.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable payment request response data contract.
 *
 * @param id the id
 * @param approvalRequestNumber the approval request number
 * @param status the status
 * @param workflowVersion the workflow version
 * @param branchId the branch id
 * @param branchName the branch name
 * @param amount the amount
 * @param note the note
 * @param submittedAt the submitted at
 * @param resolvedAt the resolved at
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
public record PaymentRequestResponse(
        Long id,
        String approvalRequestNumber,
        ApprovalRequestStatus status,
        Integer workflowVersion,
        Long branchId,
        String branchName,
        BigDecimal amount,
        String note,
        LocalDateTime submittedAt,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
