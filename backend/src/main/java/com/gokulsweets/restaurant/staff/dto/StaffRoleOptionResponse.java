package com.gokulsweets.restaurant.staff.dto;

import java.util.Set;

/**
 * Immutable staff role option response data contract.
 *
 * @param name the name
 * @param description the description
 * @param permissions the permissions
 */
public record StaffRoleOptionResponse(String name, String description, Set<String> permissions) {}
