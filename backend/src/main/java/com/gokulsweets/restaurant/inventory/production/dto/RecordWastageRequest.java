package com.gokulsweets.restaurant.inventory.production.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RecordWastageRequest(
        @NotNull(message = "Wastage quantity is required.")
        @DecimalMin(value = "0.001", message = "Wastage quantity must be greater than zero.")
        BigDecimal quantity,

        @NotBlank(message = "Wastage reason is required.")
        @Size(max = 500, message = "Wastage reason must not exceed 500 characters.")
        String reason
) {
}
