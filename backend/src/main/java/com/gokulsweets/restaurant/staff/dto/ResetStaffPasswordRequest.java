package com.gokulsweets.restaurant.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Immutable reset staff password request data contract.
 *
 * @param newPassword the new password
 */
public record ResetStaffPasswordRequest(@NotBlank @Size(min = 8, max = 100) String newPassword) {}
