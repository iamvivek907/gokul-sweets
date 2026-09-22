package com.gokulsweets.restaurant.order.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminStartSelectedPreparationRequest(

        @NotNull(
                message = "Branch ID is required."
        )
        Long branchId,

        @NotEmpty(
                message = "At least one order number is required."
        )
        @Size(
                max = 50,
                message = "A maximum of 50 orders can be started at once."
        )
        List<
                @Valid
                @NotBlank(
                        message = "Order number must not be blank."
                )
                        String
                > orderNumbers

) {
}