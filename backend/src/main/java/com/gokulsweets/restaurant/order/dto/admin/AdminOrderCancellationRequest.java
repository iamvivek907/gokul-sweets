package com.gokulsweets.restaurant.order.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminOrderCancellationRequest(
        @NotBlank(message = "Cancellation reason is required.")
        @Size(max = 300, message = "Cancellation reason must not exceed 300 characters.")
        String reason
) {
}
