package com.gokulsweets.restaurant.staff.leave.dto;

import jakarta.validation.constraints.Size;

/** Immutable leave action request data contract. */
public record LeaveActionRequest(@Size(max = 1000) String comment) {}
