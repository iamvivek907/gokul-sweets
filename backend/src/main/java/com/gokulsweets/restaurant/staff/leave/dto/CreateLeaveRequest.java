package com.gokulsweets.restaurant.staff.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateLeaveRequest(

        @NotNull
        Long branchId,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @NotBlank
        @Size(max = 1000)
        String reason
) {
}
