package com.gokulsweets.restaurant.staff.payroll.dto;

import jakarta.validation.constraints.Size;

public record PayrollActionRequest(

        @Size(max = 1000)
        String comment
) {
}
