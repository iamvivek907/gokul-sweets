package com.gokulsweets.restaurant.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CustomerOrderHistoryRequest(

        @NotEmpty(message = "At least one order number is required.")
        @Size(
                max = 500,
                message = "A maximum of 500 orders can be loaded at once."
        )
        List<
                @NotBlank(message = "Order number cannot be blank.")
                @Size(
                        max = 50,
                        message = "Order number is too long."
                )
                        String
                > orderNumbers

) {
}
