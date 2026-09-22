package com.gokulsweets.restaurant.staff.payroll.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

        LocalDateTime updatedAt
) {
}
