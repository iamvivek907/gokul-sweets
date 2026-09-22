package com.gokulsweets.restaurant.inventory.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RecordStockAdjustmentRequest(
        @NotNull(message = "Adjustment quantity is required.")
        BigDecimal quantityDelta,

        @NotBlank(message = "Adjustment reason is required.")
        @Size(max = 500, message = "Adjustment reason must not exceed 500 characters.")
        String reason
) {
}
