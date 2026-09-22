package com.gokulsweets.restaurant.staff.approval.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestType;

import java.util.List;

public record ApprovalOptionsResponse(

        List<BranchOption> branches,

        List<ApprovalRequestType> requestTypes,

        List<ApprovalRequestStatus> statuses
) {

    public record BranchOption(

            Long id,

            String code,

            String name,

            boolean active
    ) {
    }
}
