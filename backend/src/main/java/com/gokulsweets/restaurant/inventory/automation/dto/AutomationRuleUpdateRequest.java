package com.gokulsweets.restaurant.inventory.automation.dto;

import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationMode;
import com.gokulsweets.restaurant.inventory.automation.enums.InventorySeasonalMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record AutomationRuleUpdateRequest(
        @NotNull InventoryAutomationMode automationMode,
        @NotNull @DecimalMin("0.000") BigDecimal guaranteedQuantity,
        boolean forecastEnabled,
        @NotNull @Min(1) @Max(52) Integer lookbackWeeks,
        @NotNull @Min(1) @Max(52) Integer minimumHistoryDays,
        @NotNull @DecimalMin(value = "0.001") BigDecimal demandMultiplier,
        @DecimalMin(value = "0.001") BigDecimal maximumSuggestedQuantity,
        @NotNull @Min(1) @Max(127) Integer availableDaysMask,
        @NotNull InventorySeasonalMode seasonalMode,
        @Min(0) @Max(365) Integer generationHorizonDays,
        boolean active,
        List<@Valid AvailabilityWindowRequest> windows
) {
}
