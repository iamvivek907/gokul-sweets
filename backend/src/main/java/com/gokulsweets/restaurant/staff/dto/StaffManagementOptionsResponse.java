package com.gokulsweets.restaurant.staff.dto;

import java.util.List;

/**
 * Immutable staff management options response data contract.
 *
 * @param roles the roles
 * @param branches the branches
 */
public record StaffManagementOptionsResponse(
        List<StaffRoleOptionResponse> roles, List<StaffBranchOptionResponse> branches) {}
