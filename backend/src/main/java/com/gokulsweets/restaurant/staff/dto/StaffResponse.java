package com.gokulsweets.restaurant.staff.dto;

import java.util.Set;

/** Immutable staff response data contract. */
public record StaffResponse(
        Long id,
        String username,
        String fullName,
        String phone,
        boolean active,
        String roleName,
        Set<Long> branchIds) {}
