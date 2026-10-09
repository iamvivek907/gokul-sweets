package com.gokulsweets.restaurant.inventory.automation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable automation rule response data contract.
 *
 * @param branchProductId the branch product id
 * @param productId the product id
 * @param productCode the product code
 * @param productName the product name
 * @param categoryName the category name
 * @param inventoryUnit the inventory unit
 * @param onlineEnabled the online enabled
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
 * @param readyStockRequired the ready stock required
 * @param bookingHorizonDays the booking horizon days
 * @param productionLeadMinutes the production lead minutes
 */
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
        int productionLeadMinutes) {

    /**
     * Immutable window data contract.
     *
     * @param id the id
     * @param name the name
     * @param startDate the start date
     * @param endDate the end date
     * @param active the active
     */
    public record Window(
            Long id, String name, LocalDate startDate, LocalDate endDate, boolean active) {}
}
