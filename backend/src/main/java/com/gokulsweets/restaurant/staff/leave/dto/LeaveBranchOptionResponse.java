package com.gokulsweets.restaurant.staff.leave.dto;

/** Immutable leave branch option response data contract. */
public record LeaveBranchOptionResponse(Long id, String code, String name, boolean active) {}
