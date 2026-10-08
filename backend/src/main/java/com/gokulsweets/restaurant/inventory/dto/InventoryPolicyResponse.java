package com.gokulsweets.restaurant.inventory.dto;

import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import com.gokulsweets.restaurant.observability.MethodTiming;

import java.math.BigDecimal;

/** Immutable inventory policy response data contract. */
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
        Integer shelfLifeMinutes) {

    /**
     * Froms the operation.
     *
     * @param policy the policy
     * @return the from result
     */
    public static InventoryPolicyResponse from(BranchInventoryPolicy policy) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryPolicyResponse.class, "from(BranchInventoryPolicy)");
        try {
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
                    policy.getShelfLifeMinutes());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryPolicyResponse.class,
                    "from(BranchInventoryPolicy)");
        }
    }
}
