package com.gokulsweets.restaurant.staff.approval.dto;

public record ApprovalCountsResponse(

        long pending,

        long sentBack,

        long approved,

        long rejected,

        long cancelled,

        long total
) {
}
