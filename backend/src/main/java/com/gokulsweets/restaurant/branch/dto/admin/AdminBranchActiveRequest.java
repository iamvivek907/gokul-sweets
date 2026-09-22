package com.gokulsweets.restaurant.branch.dto.admin;

import jakarta.validation.constraints.NotNull;

public record AdminBranchActiveRequest(

        @NotNull
        Boolean active
) {
}