package com.gokulsweets.restaurant.staff.payroll.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestAction;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;

import java.time.LocalDateTime;

/**
 * Immutable payroll payment approval history response data contract.
 *
 * @param id the id
 * @param action the action
 * @param fromStatus the from status
 * @param toStatus the to status
 * @param actorName the actor name
 * @param comment the comment
 * @param createdAt the created at
 */
public record PayrollPaymentApprovalHistoryResponse(
        Long id,
        ApprovalRequestAction action,
        ApprovalRequestStatus fromStatus,
        ApprovalRequestStatus toStatus,
        String actorName,
        String comment,
        LocalDateTime createdAt) {}
