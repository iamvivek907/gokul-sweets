package com.gokulsweets.restaurant.staff.payroll.dto;

import java.util.List;

public record PayrollOptionsResponse(

        List<PayrollBranchOptionResponse> branches
) {
}
