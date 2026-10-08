package com.gokulsweets.restaurant.staff.payroll.dto;

/** Immutable admin payroll staff option response data contract. */
public record AdminPayrollStaffOptionResponse(
        Long id, String username, String fullName, String roleName, boolean active) {}
