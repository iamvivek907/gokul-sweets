package com.gokulsweets.restaurant.staff.payroll.dto;

/** Immutable payroll branch option response data contract. */
public record PayrollBranchOptionResponse(Long id, String code, String name, boolean active) {}
