package com.gokulsweets.restaurant.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminAllocationApprovalRequest(
        @DecimalMin("0.001") BigDecimal approvedQuantity,
        @DecimalMin("0.000") BigDecimal safetyBufferQuantity,
        @DecimalMin("0.000") BigDecimal forecastQuantity,
        @Size(max = 20) String forecastConfidence,
        LocalDateTime expectedReadyAt,
        @Size(max = 500) String note
) {
}

