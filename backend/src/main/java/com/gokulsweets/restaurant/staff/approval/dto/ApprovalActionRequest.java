package com.gokulsweets.restaurant.staff.approval.dto;

import jakarta.validation.constraints.Size;

public record ApprovalActionRequest(

        @Size(max = 1000)
        String comment
) {
}
