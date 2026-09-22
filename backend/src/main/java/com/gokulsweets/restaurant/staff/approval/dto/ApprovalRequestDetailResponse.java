package com.gokulsweets.restaurant.staff.approval.dto;

import java.util.List;

public record ApprovalRequestDetailResponse(

        ApprovalRequestResponse request,

        List<ApprovalHistoryResponse> history
) {
}
