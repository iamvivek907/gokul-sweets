package com.gokulsweets.restaurant.staff.payroll.dto;

public record AdminPayrollStaffOptionResponse(

        Long id,

        String username,

        String fullName,

        String roleName,

        boolean active
) {
}
