package com.gokulsweets.restaurant.staff.leave.dto;

import java.util.List;

public record LeaveOptionsResponse(

        List<LeaveBranchOptionResponse> branches
) {
}
