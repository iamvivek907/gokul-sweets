package com.gokulsweets.restaurant.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetStaffPasswordRequest(

        @NotBlank
        @Size(min = 8, max = 100)
        String newPassword
) {
}