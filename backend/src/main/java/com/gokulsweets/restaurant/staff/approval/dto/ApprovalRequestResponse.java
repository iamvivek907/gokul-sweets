package com.gokulsweets.restaurant.staff.approval.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestType;

import java.time.LocalDateTime;

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

        LocalDateTime updatedAt
) {
}
