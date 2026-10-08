package com.gokulsweets.restaurant.staff.approval.dto;

import java.util.List;

/** Immutable approval request detail response data contract. */
public record ApprovalRequestDetailResponse(
        ApprovalRequestResponse request, List<ApprovalHistoryResponse> history) {}
