package com.gokulsweets.restaurant.staff.attendance.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestAction;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;

import java.time.LocalDateTime;

public record AttendanceApprovalHistoryResponse(

        Long id,

        ApprovalRequestAction action,

        ApprovalRequestStatus fromStatus,

        ApprovalRequestStatus toStatus,

        String actorName,

        String comment,

        LocalDateTime createdAt
) {
}
