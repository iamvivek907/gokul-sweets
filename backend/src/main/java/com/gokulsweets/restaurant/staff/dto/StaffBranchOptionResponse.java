package com.gokulsweets.restaurant.staff.dto;

public record StaffBranchOptionResponse(
        Long id,
        String code,
        String name,
        boolean active
) {
}
