package com.gokulsweets.restaurant.staff.approval.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestType;

import java.time.LocalDateTime;

/**
 * Immutable approval request response data contract.
 *
 * @param id the id
 * @param requestNumber the request number
 * @param requestType the request type
 * @param status the status
 * @param staffUserId the staff user id
 * @param staffName the staff name
 * @param staffUsername the staff username
 * @param branchId the branch id
 * @param branchCode the branch code
 * @param branchName the branch name
 * @param title the title
 * @param summary the summary
 * @param workflowVersion the workflow version
 * @param submittedAt the submitted at
 * @param resolvedAt the resolved at
 * @param createdAt the created at
 * @param updatedAt the updated at
 */
public record ApprovalRequestResponse(
        Long id,
        String requestNumber,
        ApprovalRequestType requestType,
        ApprovalRequestStatus status,
        Long staffUserId,
        String staffName,
        String staffUsername,
        Long branchId,
        String branchCode,
        String branchName,
        String title,
        String summary,
        Integer workflowVersion,
        LocalDateTime submittedAt,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
