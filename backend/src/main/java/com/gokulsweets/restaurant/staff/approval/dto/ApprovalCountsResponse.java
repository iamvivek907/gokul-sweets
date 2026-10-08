package com.gokulsweets.restaurant.staff.approval.dto;

/** Immutable approval counts response data contract. */
public record ApprovalCountsResponse(
        long pending, long sentBack, long approved, long rejected, long cancelled, long total) {}
