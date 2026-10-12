package com.gokulsweets.restaurant.staff.leave.dto;

import jakarta.validation.constraints.Size;

/**
 * Immutable leave action request data contract.
 *
 * @param comment the comment
 */
public record LeaveActionRequest(@Size(max = 1000) String comment) {}
