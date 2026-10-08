package com.gokulsweets.restaurant.staff.leave.dto;

/**
 * Immutable leave branch option response data contract.
 *
 * @param id the id
 * @param code the code
 * @param name the name
 * @param active the active
 */
public record LeaveBranchOptionResponse(Long id, String code, String name, boolean active) {}
