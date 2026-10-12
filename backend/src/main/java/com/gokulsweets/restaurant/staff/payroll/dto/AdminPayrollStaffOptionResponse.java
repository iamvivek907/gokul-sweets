package com.gokulsweets.restaurant.staff.payroll.dto;

/**
 * Immutable admin payroll staff option response data contract.
 *
 * @param id the id
 * @param username the username
 * @param fullName the full name
 * @param roleName the role name
 * @param active the active
 */
public record AdminPayrollStaffOptionResponse(
        Long id, String username, String fullName, String roleName, boolean active) {}
