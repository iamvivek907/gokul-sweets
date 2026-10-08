package com.gokulsweets.restaurant.staff.approval.dto;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestType;

import java.util.List;

/**
 * Immutable approval options response data contract.
 *
 * @param branches the branches
 * @param requestTypes the request types
 * @param statuses the statuses
 */
public record ApprovalOptionsResponse(
        List<BranchOption> branches,
        List<ApprovalRequestType> requestTypes,
        List<ApprovalRequestStatus> statuses) {

    /**
     * Immutable branch option data contract.
     *
     * @param id the id
     * @param code the code
     * @param name the name
     * @param active the active
     */
    public record BranchOption(Long id, String code, String name, boolean active) {}
}
