package com.gokulsweets.restaurant.inventory.production.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RecordProductionRequest(
        @NotNull(message = "Produced quantity is required.")
        @DecimalMin(value = "0.001", message = "Produced quantity must be greater than zero.")
        BigDecimal quantity,

        @Size(max = 500, message = "Note must not exceed 500 characters.")
        String note
) {
}
