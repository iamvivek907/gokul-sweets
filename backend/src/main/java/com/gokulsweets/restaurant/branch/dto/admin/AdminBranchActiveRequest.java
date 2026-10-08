package com.gokulsweets.restaurant.branch.dto.admin;

import jakarta.validation.constraints.NotNull;

/** Immutable admin branch active request data contract. */
public record AdminBranchActiveRequest(@NotNull Boolean active) {}
