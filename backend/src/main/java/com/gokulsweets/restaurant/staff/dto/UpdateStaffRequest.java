package com.gokulsweets.restaurant.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateStaffRequest(

        @NotBlank
        @Size(max = 150)
        String fullName,

        String phone,

        @NotBlank
        String roleName,

        Set<Long> branchIds,

        boolean active
) {
}