package com.gokulsweets.restaurant.branch.dto.admin;

import jakarta.validation.constraints.NotNull;

/**
 * Immutable admin branch active request data contract.
 *
 * @param active the active
 */
public record AdminBranchActiveRequest(@NotNull Boolean active) {}
