package com.gokulsweets.restaurant.inventory.automation.dto;

import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationMode;
import com.gokulsweets.restaurant.inventory.automation.enums.InventorySeasonalMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable automation rule update request data contract.
 *
 * @param automationMode the automation mode
 * @param guaranteedQuantity the guaranteed quantity
 * @param forecastEnabled the forecast enabled
 * @param lookbackWeeks the lookback weeks
 * @param minimumHistoryDays the minimum history days
 * @param demandMultiplier the demand multiplier
 * @param maximumSuggestedQuantity the maximum suggested quantity
 * @param availableDaysMask the available days mask
 * @param seasonalMode the seasonal mode
 * @param generationHorizonDays the generation horizon days
 * @param active the active
 * @param windows the windows
 */
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
        List<@Valid AvailabilityWindowRequest> windows) {}
