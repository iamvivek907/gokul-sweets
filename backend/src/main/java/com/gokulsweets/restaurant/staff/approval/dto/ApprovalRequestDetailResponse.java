package com.gokulsweets.restaurant.staff.approval.dto;

import java.util.List;

/**
 * Immutable approval request detail response data contract.
 *
 * @param request the request
 * @param history the history
 */
public record ApprovalRequestDetailResponse(
        ApprovalRequestResponse request, List<ApprovalHistoryResponse> history) {}
