package com.gokulsweets.restaurant.staff.approval.dto;

import jakarta.validation.constraints.Size;

/**
 * Immutable approval action request data contract.
 *
 * @param comment the comment
 */
public record ApprovalActionRequest(@Size(max = 1000) String comment) {}
