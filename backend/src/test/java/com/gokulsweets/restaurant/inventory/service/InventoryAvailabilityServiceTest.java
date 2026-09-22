package com.gokulsweets.restaurant.inventory.service;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryAvailabilityServiceTest {

    private final InventoryAvailabilityService service =
            new InventoryAvailabilityService();

    @Test
    void calculatesAvailableToPromiseFromApprovedSupply() {
        BranchInventoryPolicy policy = policy(false);
        InventoryDailyAllocation allocation = allocation();

        allocation.setApprovedQuantity(new BigDecimal("10.000"));
        allocation.setSafetyBufferQuantity(new BigDecimal("1.000"));
        allocation.setHeldQuantity(new BigDecimal("0.500"));
        allocation.setCommittedQuantity(new BigDecimal("5.500"));

        InventoryAvailability result =
                service.calculate(allocation, policy);

        assertThat(result.orderable()).isTrue();
        assertThat(result.availableQuantity())
                .isEqualByComparingTo("3.000");
    }

    @Test
    void readyStockPolicyUsesTheLowerReadyQuantity() {
        BranchInventoryPolicy policy = policy(true);
        InventoryDailyAllocation allocation = allocation();

        allocation.setStatus(InventoryAllocationStatus.READY);
        allocation.setApprovedQuantity(new BigDecimal("10.000"));
        allocation.setReadyQuantity(new BigDecimal("6.000"));
        allocation.setSafetyBufferQuantity(new BigDecimal("1.000"));
        allocation.setCommittedQuantity(new BigDecimal("2.000"));

        InventoryAvailability result =
                service.calculate(allocation, policy);

        assertThat(result.availableQuantity())
                .isEqualByComparingTo("3.000");
    }

    @Test
    void draftAllocationIsNeverOrderable() {
        BranchInventoryPolicy policy = policy(false);
        InventoryDailyAllocation allocation = allocation();
        allocation.setStatus(InventoryAllocationStatus.DRAFT);
        allocation.setApprovedQuantity(new BigDecimal("10.000"));

        InventoryAvailability result =
                service.calculate(allocation, policy);

        assertThat(result.orderable()).isFalse();
        assertThat(result.unavailableReason())
                .contains("awaiting approval");
    }

    private BranchInventoryPolicy policy(
            boolean readyStockRequired
    ) {
        BranchInventoryPolicy policy =
                new BranchInventoryPolicy();
        policy.setOnlineEnabled(true);
        policy.setReadyStockRequired(readyStockRequired);
        return policy;
    }

    private InventoryDailyAllocation allocation() {
        BranchProduct branchProduct = new BranchProduct();
        branchProduct.setId(10L);

        InventoryDailyAllocation allocation =
                new InventoryDailyAllocation();
        allocation.setBranchProduct(branchProduct);
        allocation.setServiceDate(LocalDate.of(2026, 9, 20));
        allocation.setInventoryUnit(InventoryUnit.GRAM);
        allocation.setStatus(InventoryAllocationStatus.APPROVED);
        return allocation;
    }
}

