package com.gokulsweets.restaurant.staff.leave.dto;

import java.util.List;

/**
 * Immutable leave options response data contract.
 *
 * @param branches the branches
 */
public record LeaveOptionsResponse(List<LeaveBranchOptionResponse> branches) {}
