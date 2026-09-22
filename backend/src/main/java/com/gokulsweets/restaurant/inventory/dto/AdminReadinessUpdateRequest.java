package com.gokulsweets.restaurant.inventory.dto;

import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminReadinessUpdateRequest(
        @NotNull InventoryAllocationStatus status,
        @NotNull @DecimalMin("0.000") BigDecimal readyQuantity,
        LocalDateTime expectedReadyAt,
        @Size(max = 500) String note
) {
}

