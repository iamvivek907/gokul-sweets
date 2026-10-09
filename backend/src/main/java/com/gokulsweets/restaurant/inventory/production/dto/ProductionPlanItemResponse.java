package com.gokulsweets.restaurant.inventory.production.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable production plan item response data contract.
 *
 * @param branchProductId the branch product id
 * @param productId the product id
 * @param productCode the product code
 * @param productName the product name
 * @param categoryName the category name
 * @param inventoryUnit the inventory unit
 * @param controlMode the control mode
 * @param allocationStatus the allocation status
 * @param priority the priority
 * @param approvedQuantity the approved quantity
 * @param heldQuantity the held quantity
 * @param committedQuantity the committed quantity
 * @param outstandingConfirmedQuantity the outstanding confirmed quantity
 * @param readyQuantity the ready quantity
 * @param fulfilledQuantity the fulfilled quantity
 * @param wastedQuantity the wasted quantity
 * @param physicalOnHandQuantity the physical on hand quantity
 * @param safetyBufferQuantity the safety buffer quantity
 * @param forecastQuantity the forecast quantity
 * @param forecastConfidence the forecast confidence
 * @param minimumToPrepareQuantity the minimum to prepare quantity
 * @param suggestedToPrepareQuantity the suggested to prepare quantity
 * @param expectedReadyAt the expected ready at
 * @param note the note
 */
public record ProductionPlanItemResponse(
        Long branchProductId,
        Long productId,
        String productCode,
        String productName,
        String categoryName,
        String inventoryUnit,
        String controlMode,
        String allocationStatus,
        String priority,
        BigDecimal approvedQuantity,
        BigDecimal heldQuantity,
        BigDecimal committedQuantity,
        BigDecimal outstandingConfirmedQuantity,
        BigDecimal readyQuantity,
        BigDecimal fulfilledQuantity,
        BigDecimal wastedQuantity,
        BigDecimal physicalOnHandQuantity,
        BigDecimal safetyBufferQuantity,
        BigDecimal forecastQuantity,
        String forecastConfidence,
        BigDecimal minimumToPrepareQuantity,
        BigDecimal suggestedToPrepareQuantity,
        LocalDateTime expectedReadyAt,
        String note) {}
