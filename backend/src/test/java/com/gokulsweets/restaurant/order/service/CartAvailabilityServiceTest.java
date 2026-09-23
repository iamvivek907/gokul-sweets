package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryAvailabilityService;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.pickup.BranchPickupSettingsRepository;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class CartAvailabilityServiceTest {

    @Test
    void excludesSameDaySlotsBeforePreparationLeadTime() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-23T03:45:00Z"),
                zone
        );

        EnhancementProperties features = new EnhancementProperties();
        features.setFutureOrderingDays(7);

        InventoryProperties inventory = new InventoryProperties();
        inventory.setEnforcementEnabled(false);

        BranchInventoryPolicyRepository policyRepository = Mockito.mock(BranchInventoryPolicyRepository.class);
        InventoryDailyAllocationRepository allocationRepository = Mockito.mock(InventoryDailyAllocationRepository.class);
        OrderValidationService orderValidation = Mockito.mock(OrderValidationService.class);
        PickupSlotRepository slotRepository = Mockito.mock(PickupSlotRepository.class);
        BranchPickupSettingsRepository settingsRepository = Mockito.mock(BranchPickupSettingsRepository.class);
        InventoryAvailabilityService inventoryAvailability = Mockito.mock(InventoryAvailabilityService.class);

        SmartOrderingRules rules = new SmartOrderingRules(features, settingsRepository, clock);
        CartAvailabilityService service = new CartAvailabilityService(
                features,
                inventory,
                orderValidation,
                rules,
                policyRepository,
                allocationRepository,
                inventoryAvailability,
                slotRepository,
                settingsRepository,
                clock
        );

        long branchId = 10L;
        LocalDate date = LocalDate.of(2026, 9, 23);
        long branchProductId = 500L;

        Branch branch = new Branch();
        branch.setId(branchId);

        BranchProduct branchProduct = new BranchProduct();
        branchProduct.setId(branchProductId);
        branchProduct.setBranch(branch);

        Product product = new Product();
        product.setId(700L);
        product.setName("Mysore Pak");

        ValidatedOrderItem validatedItem = new ValidatedOrderItem(
                product,
                branchProduct,
                ProductSaleMode.UNIT,
                1,
                null
        );

        BranchInventoryPolicy policy = new BranchInventoryPolicy();
        policy.setBranchProduct(branchProduct);
        policy.setControlMode(InventoryControlMode.DAILY_PRODUCTION);
        policy.setInventoryUnit(InventoryUnit.PIECE);
        policy.setOnlineEnabled(true);
        policy.setReadyStockRequired(false);
        policy.setBookingHorizonDays(7);
        policy.setProductionLeadMinutes(90);

        PickupSlot early = slot(branch, date, 1000L, LocalTime.of(10, 0), LocalTime.of(10, 30));
        PickupSlot valid = slot(branch, date, 1001L, LocalTime.of(12, 0), LocalTime.of(12, 30));

        when(orderValidation.validateCart(any(), anyList()))
                .thenReturn(List.of(validatedItem));
        when(policyRepository.findByBranchProductIdIn(anyList()))
                .thenReturn(List.of(policy));
        when(settingsRepository.findByBranchId(branchId))
                .thenReturn(Optional.empty());
        when(slotRepository.findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
                branchId,
                date,
                date
        )).thenReturn(List.of(early, valid));

        CartAvailabilityService.Availability availability = service.check(
                branchId,
                date,
                1,
                List.of(new CreateOrderItemRequest(product.getId(), 1, null))
        );

        CartAvailabilityService.DateAvailability day = availability.dates().getFirst();
        assertThat(day.available()).isTrue();
        assertThat(day.slots()).hasSize(2);

        CartAvailabilityService.SlotAvailability earlySlot = day.slots().get(0);
        assertThat(earlySlot.normalAvailable()).isFalse();
        assertThat(earlySlot.priorityAvailable()).isFalse();
        assertThat(earlySlot.reason()).contains("preparation time");

        CartAvailabilityService.SlotAvailability validSlot = day.slots().get(1);
        assertThat(validSlot.normalAvailable()).isTrue();
    }

    private PickupSlot slot(
            Branch branch,
            LocalDate date,
            Long id,
            LocalTime start,
            LocalTime end
    ) {
        PickupSlot slot = new PickupSlot();
        slot.setId(id);
        slot.setBranch(branch);
        slot.setSlotDate(date);
        slot.setStartTime(start);
        slot.setEndTime(end);
        slot.setActive(true);
        slot.setCapacity(10);
        slot.setBookedCount(0);
        slot.setPriorityEnabled(true);
        slot.setPriorityCapacity(2);
        slot.setPriorityBookedCount(0);
        slot.setPriorityCharge(BigDecimal.TEN);
        return slot;
    }
}
