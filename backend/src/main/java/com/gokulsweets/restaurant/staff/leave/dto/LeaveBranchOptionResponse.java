package com.gokulsweets.restaurant.staff.leave.dto;

public record LeaveBranchOptionResponse(

        Long id,

        String code,

        String name,

        boolean active
) {
}
