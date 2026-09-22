package com.gokulsweets.restaurant.staff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateStaffRequest(

        @NotBlank
        @Size(max = 100)
        String username,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        @NotBlank
        @Size(max = 150)
        String fullName,

        String phone,

        @NotBlank
        String roleName,

        Set<Long> branchIds,

        @Valid
        CreateStaffPayrollSetupRequest payroll
) {
}
