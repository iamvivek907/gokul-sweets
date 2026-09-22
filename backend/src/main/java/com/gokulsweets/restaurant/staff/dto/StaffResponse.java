package com.gokulsweets.restaurant.staff.dto;

import java.util.Set;

public record StaffResponse(

        Long id,

        String username,

        String fullName,

        String phone,

        boolean active,

        String roleName,

        Set<Long> branchIds
) {
}