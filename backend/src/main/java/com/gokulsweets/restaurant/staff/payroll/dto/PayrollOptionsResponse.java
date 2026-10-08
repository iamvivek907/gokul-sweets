package com.gokulsweets.restaurant.staff.payroll.dto;

import java.util.List;

/** Immutable payroll options response data contract. */
public record PayrollOptionsResponse(List<PayrollBranchOptionResponse> branches) {}
