package com.gokulsweets.restaurant.staff.attendance.dto;

public record AttendanceBranchOptionResponse(

        Long id,

        String code,

        String name,

        boolean active
) {
}
