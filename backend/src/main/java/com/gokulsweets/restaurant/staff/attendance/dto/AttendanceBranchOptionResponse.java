package com.gokulsweets.restaurant.staff.attendance.dto;

/** Immutable attendance branch option response data contract. */
public record AttendanceBranchOptionResponse(Long id, String code, String name, boolean active) {}
