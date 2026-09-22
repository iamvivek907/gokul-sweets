package com.gokulsweets.restaurant.staff.dto;

import java.util.Set;

public record StaffRoleOptionResponse(
        String name,
        String description,
        Set<String> permissions
) {
}
