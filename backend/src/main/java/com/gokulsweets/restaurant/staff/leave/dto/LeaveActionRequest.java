package com.gokulsweets.restaurant.staff.leave.dto;

import jakarta.validation.constraints.Size;

public record LeaveActionRequest(

        @Size(max = 1000)
        String comment
) {
}
