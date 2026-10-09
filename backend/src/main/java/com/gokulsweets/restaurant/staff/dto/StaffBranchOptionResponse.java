package com.gokulsweets.restaurant.staff.dto;

/**
 * Immutable staff branch option response data contract.
 *
 * @param id the id
 * @param code the code
 * @param name the name
 * @param active the active
 */
public record StaffBranchOptionResponse(Long id, String code, String name, boolean active) {}
