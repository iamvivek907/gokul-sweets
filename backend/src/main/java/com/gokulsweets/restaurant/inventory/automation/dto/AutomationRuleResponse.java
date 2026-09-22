package com.gokulsweets.restaurant.inventory.automation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AutomationRuleResponse(
        Long branchProductId,
        Long productId,
        String productCode,
        String productName,
        String categoryName,
        String inventoryUnit,
        boolean onlineEnabled,
        String automationMode,
        BigDecimal guaranteedQuantity,
        boolean forecastEnabled,
        int lookbackWeeks,
        int minimumHistoryDays,
        BigDecimal demandMultiplier,
        BigDecimal maximumSuggestedQuantity,
        int availableDaysMask,
        String seasonalMode,
        Integer generationHorizonDays,
        boolean active,
        List<Window> windows,
        boolean readyStockRequired,
        int bookingHorizonDays,
        int productionLeadMinutes
) {
    public record Window(
            Long id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean active
    ) {
    }
}
