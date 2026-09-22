package com.gokulsweets.restaurant.inventory.dto;

import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;

import java.math.BigDecimal;

public record InventoryPolicyResponse(
        Long id,
        Long branchProductId,
        Long branchId,
        Long productId,
        String productName,
        InventoryControlMode controlMode,
        InventoryUnit inventoryUnit,
        boolean onlineEnabled,
        boolean readyStockRequired,
        BigDecimal defaultSafetyBuffer,
        BigDecimal maximumDailyAllocation,
        Integer bookingHorizonDays,
        Integer productionLeadMinutes,
        Integer shelfLifeMinutes
) {

    public static InventoryPolicyResponse from(
            BranchInventoryPolicy policy
    ) {
        return new InventoryPolicyResponse(
                policy.getId(),
                policy.getBranchProduct().getId(),
                policy.getBranchProduct().getBranch().getId(),
                policy.getBranchProduct().getProduct().getId(),
                policy.getBranchProduct().getProduct().getName(),
                policy.getControlMode(),
                policy.getInventoryUnit(),
                policy.isOnlineEnabled(),
                policy.isReadyStockRequired(),
                policy.getDefaultSafetyBuffer(),
                policy.getMaximumDailyAllocation(),
                policy.getBookingHorizonDays(),
                policy.getProductionLeadMinutes(),
                policy.getShelfLifeMinutes()
        );
    }
}

