package com.gokulsweets.restaurant.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable admin complete inventory item request data contract.
 *
 * @param branchProductId the branch product id
 * @param approvedQuantity the approved quantity
 * @param readyQuantity the ready quantity
 * @param markReady the mark ready
 * @param safetyBufferQuantity the safety buffer quantity
 * @param forecastQuantity the forecast quantity
 * @param forecastConfidence the forecast confidence
 * @param expectedReadyAt the expected ready at
 * @param note the note
 */
public record AdminCompleteInventoryItemRequest(
        @NotNull(message = "Branch product ID is required.") Long branchProductId,
        @NotNull(message = "Approved quantity is required.")
                @DecimalMin(
                        value = "0.001",
                        message = "Approved quantity must be greater than zero.")
                BigDecimal approvedQuantity,
        @NotNull(message = "Ready quantity is required.")
                @DecimalMin(value = "0.000", message = "Ready quantity cannot be negative.")
                BigDecimal readyQuantity, /*
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
        String note) {

    /**
     * Creates a admin complete inventory item request instance.
     *
     * @param branchProductId the branch product id
     * @param approvedQuantity the approved quantity
     * @param readyQuantity the ready quantity
     * @param safetyBufferQuantity the safety buffer quantity
     * @param forecastQuantity the forecast quantity
     * @param forecastConfidence the forecast confidence
     * @param expectedReadyAt the expected ready at
     * @param note the note
     */
    public AdminCompleteInventoryItemRequest(
            Long branchProductId,
            BigDecimal approvedQuantity,
            BigDecimal readyQuantity,
            BigDecimal safetyBufferQuantity,
            BigDecimal forecastQuantity,
            String forecastConfidence,
            LocalDateTime expectedReadyAt,
            String note) {
        this(
                branchProductId,
                approvedQuantity,
                readyQuantity,
                null,
                safetyBufferQuantity,
                forecastQuantity,
                forecastConfidence,
                expectedReadyAt,
                note);
    }
}
