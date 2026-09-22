package com.gokulsweets.restaurant.inventory.production.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        String note
) {
}
