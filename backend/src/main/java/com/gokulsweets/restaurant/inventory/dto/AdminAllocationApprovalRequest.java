package com.gokulsweets.restaurant.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable admin allocation approval request data contract.
 *
 * @param approvedQuantity the approved quantity
 * @param safetyBufferQuantity the safety buffer quantity
 * @param forecastQuantity the forecast quantity
 * @param forecastConfidence the forecast confidence
 * @param expectedReadyAt the expected ready at
 * @param note the note
 */
public record AdminAllocationApprovalRequest(
        @DecimalMin("0.001") BigDecimal approvedQuantity,
        @DecimalMin("0.000") BigDecimal safetyBufferQuantity,
        @DecimalMin("0.000") BigDecimal forecastQuantity,
        @Size(max = 20) String forecastConfidence,
        LocalDateTime expectedReadyAt,
        @Size(max = 500) String note) {}
