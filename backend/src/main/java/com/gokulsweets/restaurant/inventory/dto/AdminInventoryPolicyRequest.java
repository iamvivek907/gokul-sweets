package com.gokulsweets.restaurant.inventory.dto;

import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Immutable admin inventory policy request data contract.
 *
 * @param controlMode the control mode
 * @param inventoryUnit the inventory unit
 * @param onlineEnabled the online enabled
 * @param readyStockRequired the ready stock required
 * @param defaultSafetyBuffer the default safety buffer
 * @param maximumDailyAllocation the maximum daily allocation
 * @param bookingHorizonDays the booking horizon days
 * @param productionLeadMinutes the production lead minutes
 * @param shelfLifeMinutes the shelf life minutes
 */
public record AdminInventoryPolicyRequest(
        @NotNull InventoryControlMode controlMode,
        @NotNull InventoryUnit inventoryUnit,
        @NotNull Boolean onlineEnabled,
        @NotNull Boolean readyStockRequired,
        @NotNull @DecimalMin("0.000") BigDecimal defaultSafetyBuffer,
        @DecimalMin("0.001") BigDecimal maximumDailyAllocation,
        @NotNull @Min(0) @Max(365) Integer bookingHorizonDays,
        @NotNull @Min(0) Integer productionLeadMinutes,
        @Min(1) Integer shelfLifeMinutes) {}
