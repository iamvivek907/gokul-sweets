package com.gokulsweets.restaurant.staff.payroll.dto;

import jakarta.validation.constraints.Size;

/** Immutable payroll action request data contract. */
public record PayrollActionRequest(@Size(max = 1000) String comment) {}
