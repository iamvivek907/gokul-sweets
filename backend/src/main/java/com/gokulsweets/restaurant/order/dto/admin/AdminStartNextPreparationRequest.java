package com.gokulsweets.restaurant.order.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdminStartNextPreparationRequest(

        @NotNull(
                message = "Branch ID is required."
        )
        Long branchId,

        @NotNull(
                message = "Count is required."
        )
        @Min(
                value = 1,
                message = "Count must be at least 1."
        )
        @Max(
                value = 50,
                message = "Count must not exceed 50."
        )
        Integer count

) {
}