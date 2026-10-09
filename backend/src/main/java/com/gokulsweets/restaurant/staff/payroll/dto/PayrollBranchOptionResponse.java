package com.gokulsweets.restaurant.staff.payroll.dto;

/**
 * Immutable payroll branch option response data contract.
 *
 * @param id the id
 * @param code the code
 * @param name the name
 * @param active the active
 */
public record PayrollBranchOptionResponse(Long id, String code, String name, boolean active) {}
