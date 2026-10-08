package com.gokulsweets.restaurant.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable admin bulk allocation item request data contract.
 *
 * @param branchProductId the branch product id
 * @param approvedQuantity the approved quantity
 * @param safetyBufferQuantity the safety buffer quantity
 * @param forecastQuantity the forecast quantity
 * @param forecastConfidence the forecast confidence
 * @param expectedReadyAt the expected ready at
 * @param note the note
 */
public record AdminBulkAllocationItemRequest(
        @NotNull(message = "Branch product ID is required.") Long branchProductId,
        @NotNull(message = "Approved quantity is required.")
                @DecimalMin(
                        value = "0.001",
                        message = "Approved quantity must be greater than zero.")
                BigDecimal approvedQuantity,
        @DecimalMin(value = "0.000", message = "Safety buffer cannot be negative.")
                BigDecimal safetyBufferQuantity,
        @DecimalMin(value = "0.000", message = "Forecast quantity cannot be negative.")
                BigDecimal forecastQuantity,
        String forecastConfidence,
        LocalDateTime expectedReadyAt,
        String note) {}
