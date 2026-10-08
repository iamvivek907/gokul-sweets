package com.gokulsweets.restaurant.staff.attendance.dto;

import jakarta.validation.constraints.Size;

/**
 * Immutable attendance action request data contract.
 *
 * @param comment the comment
 */
public record AttendanceActionRequest(@Size(max = 1000) String comment) {}
