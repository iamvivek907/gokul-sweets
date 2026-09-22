package com.gokulsweets.restaurant.staff.dto;

import java.util.List;

public record StaffManagementOptionsResponse(
        List<StaffRoleOptionResponse> roles,
        List<StaffBranchOptionResponse> branches
) {
}
