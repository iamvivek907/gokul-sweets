package com.gokulsweets.restaurant.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminCompleteInventoryItemRequest(
        @NotNull(message = "Branch product ID is required.")
        Long branchProductId,

        @NotNull(message = "Approved quantity is required.")
        @DecimalMin(value = "0.001", message = "Approved quantity must be greater than zero.")
        BigDecimal approvedQuantity,

        @NotNull(message = "Ready quantity is required.")
        @DecimalMin(value = "0.000", message = "Ready quantity cannot be negative.")
        BigDecimal readyQuantity,

        /*
         * Null keeps older frontend clients backward compatible:
         * they historically used this endpoint only for READY stock.
         */
        Boolean markReady,

        @DecimalMin(value = "0.000", message = "Safety buffer cannot be negative.")
        BigDecimal safetyBufferQuantity,

        @DecimalMin(value = "0.000", message = "Forecast quantity cannot be negative.")
        BigDecimal forecastQuantity,

        String forecastConfidence,
        LocalDateTime expectedReadyAt,
        String note
) {

    public AdminCompleteInventoryItemRequest(
            Long branchProductId,
            BigDecimal approvedQuantity,
            BigDecimal readyQuantity,
            BigDecimal safetyBufferQuantity,
            BigDecimal forecastQuantity,
            String forecastConfidence,
            LocalDateTime expectedReadyAt,
            String note
    ) {
        this(
                branchProductId,
                approvedQuantity,
                readyQuantity,
                null,
                safetyBufferQuantity,
                forecastQuantity,
                forecastConfidence,
                expectedReadyAt,
                note
        );
    }
}
