package com.gokulsweets.restaurant.staff.approval.dto;

/**
 * Immutable approval counts response data contract.
 *
 * @param pending the pending
 * @param sentBack the sent back
 * @param approved the approved
 * @param rejected the rejected
 * @param cancelled the cancelled
 * @param total the total
 */
public record ApprovalCountsResponse(
        long pending, long sentBack, long approved, long rejected, long cancelled, long total) {}
