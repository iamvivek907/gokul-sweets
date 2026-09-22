package com.gokulsweets.restaurant.staff.approval.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestAction;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;

import java.time.LocalDateTime;

public record ApprovalHistoryResponse(

        Long id,

        ApprovalRequestAction action,

        ApprovalRequestStatus fromStatus,

        ApprovalRequestStatus toStatus,

        Long actorStaffUserId,

        String actorName,

        String comment,

        LocalDateTime createdAt
) {
}
