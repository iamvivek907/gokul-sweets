package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;

@Service
public class InventoryAvailabilityService {

    private static final int QUANTITY_SCALE = 3;

    private static final EnumSet<InventoryAllocationStatus> ORDERABLE_STATUSES =
            EnumSet.of(
                    InventoryAllocationStatus.APPROVED,
                    InventoryAllocationStatus.READY
            );

    public InventoryAvailability calculate(
            InventoryDailyAllocation allocation,
            BranchInventoryPolicy policy
    ) {
        String reason = validateOrderability(allocation, policy);

        BigDecimal supplyCeiling = policy.isReadyStockRequired()
                ? allocation.getReadyQuantity().min(allocation.getApprovedQuantity())
                : allocation.getApprovedQuantity();

        /*
         * committedQuantity remains cumulative after fulfilment so picked-up
         * stock never becomes orderable again. Wastage is separate from
         * commitments and must therefore be subtracted explicitly.
         */
        BigDecimal available = supplyCeiling
                .subtract(allocation.getSafetyBufferQuantity())
                .subtract(allocation.getHeldQuantity())
                .subtract(allocation.getCommittedQuantity())
                .subtract(allocation.getWastedQuantity())
                .max(BigDecimal.ZERO)
                .setScale(QUANTITY_SCALE, RoundingMode.DOWN);

        boolean orderable = reason == null && available.signum() > 0;
        if (reason == null && !orderable) {
            reason = "No online quantity remains for this date.";
        }

        return new InventoryAvailability(
                allocation.getBranchProduct().getId(),
                allocation.getServiceDate(),
                allocation.getInventoryUnit(),
                allocation.getStatus(),
                available,
                orderable,
                allocation.getExpectedReadyAt(),
                reason
        );
    }

    private String validateOrderability(
            InventoryDailyAllocation allocation,
            BranchInventoryPolicy policy
    ) {
        if (!policy.isOnlineEnabled()) {
            return "This product is not enabled for online inventory.";
        }
        if (!ORDERABLE_STATUSES.contains(allocation.getStatus())) {
            return switch (allocation.getStatus()) {
                case DRAFT -> "Inventory for this date is awaiting approval.";
                case DELAYED -> "Preparation is delayed for this date.";
                case UNAVAILABLE -> "This product is unavailable for this date.";
                case CLOSED -> "Online booking is closed for this date.";
                default -> "This product cannot currently be ordered.";
            };
        }
        if (policy.isReadyStockRequired()
                && allocation.getStatus() != InventoryAllocationStatus.READY) {
            return "This product will become orderable after stock is marked ready.";
        }
        return null;
    }
}
