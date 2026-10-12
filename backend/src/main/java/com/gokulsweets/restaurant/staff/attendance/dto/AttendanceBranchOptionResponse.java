package com.gokulsweets.restaurant.staff.attendance.dto;

/**
 * Immutable attendance branch option response data contract.
 *
 * @param id the id
 * @param code the code
 * @param name the name
 * @param active the active
 */
public record AttendanceBranchOptionResponse(Long id, String code, String name, boolean active) {}
