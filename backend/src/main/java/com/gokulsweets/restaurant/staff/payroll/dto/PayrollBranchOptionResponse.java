package com.gokulsweets.restaurant.staff.payroll.dto;

public record PayrollBranchOptionResponse(

        Long id,

        String code,

        String name,

        boolean active
) {
}
