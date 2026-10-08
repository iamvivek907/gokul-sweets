package com.gokulsweets.restaurant.staff.dto;

import java.util.Set;

/** Immutable staff role option response data contract. */
public record StaffRoleOptionResponse(String name, String description, Set<String> permissions) {}
