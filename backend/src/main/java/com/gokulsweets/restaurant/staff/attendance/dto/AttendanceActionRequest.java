package com.gokulsweets.restaurant.staff.attendance.dto;

import jakarta.validation.constraints.Size;

public record AttendanceActionRequest(

        @Size(max = 1000)
        String comment
) {
}
