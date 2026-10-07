package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
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
    void plannedLabelRequiresApprovedDatedCapacityForEveryItemAndDisappearsAtIstMidnight() {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        Clock beforeMidnight = Clock.fixed(Instant.parse("2026-09-29T18:29:00Z"), ist);
        LocalDate date = LocalDate.of(2026, 9, 30);
        long branchId = 10L;
        Branch branch = new Branch(); branch.setId(branchId);
        EnhancementProperties features = new EnhancementProperties();
        features.setFutureOrderingDays(7);
        features.setPlannedPickupProduction(true);
        InventoryProperties inventory = new InventoryProperties();
        inventory.setEnforcementEnabled(true);
        var policies = Mockito.mock(BranchInventoryPolicyRepository.class);
        var allocations = Mockito.mock(InventoryDailyAllocationRepository.class);
        var validation = Mockito.mock(OrderValidationService.class);
        var slotRepository = Mockito.mock(PickupSlotRepository.class);
        var settings = Mockito.mock(BranchPickupSettingsRepository.class);
        var first = branchProduct(branch, 101L, 201L, "Laddu");
        var second = branchProduct(branch, 102L, 202L, "Barfi");
        var firstPolicy = productionPolicy(first);
        var secondPolicy = productionPolicy(second);
        var firstAllocation = approved(first, date, 10);
        var secondAllocation = approved(second, date, 10);
        when(validation.validatePickupCart(any(), anyList())).thenReturn(List.of(
                new ValidatedOrderItem(first.getProduct(), first, ProductSaleMode.UNIT, 2, null),
                new ValidatedOrderItem(second.getProduct(), second, ProductSaleMode.UNIT, 2, null)));
        when(policies.findByBranchProductIdIn(anyList())).thenReturn(List.of(firstPolicy, secondPolicy));
        when(allocations.findByBranchProductIdInAndServiceDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(firstAllocation, secondAllocation));
        when(settings.findByBranchId(branchId)).thenReturn(Optional.empty());
        var slot = slot(branch, date, 1000L, LocalTime.of(12, 0), LocalTime.of(12, 30));
        when(slotRepository.findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(branchId, date, date))
                .thenReturn(List.of(slot));
        var request = List.of(new CreateOrderItemRequest(201L, 2, null),
                new CreateOrderItemRequest(202L, 2, null));
        var service = new CartAvailabilityService(features, inventory, validation,
                new SmartOrderingRules(features, settings, beforeMidnight), policies, allocations,
                new InventoryAvailabilityService(), slotRepository, settings, beforeMidnight, noServiceHours());
        assertThat(service.check(branchId, date, 1, request).dates().getFirst().plannedProduction()).isTrue();

        secondAllocation.setStatus(InventoryAllocationStatus.DRAFT);
        secondAllocation.setForecastQuantity(BigDecimal.valueOf(1000));
        assertThat(service.check(branchId, date, 1, request).dates().getFirst().plannedProduction()).isFalse();
        secondAllocation.setStatus(InventoryAllocationStatus.APPROVED);
        secondAllocation.setHeldQuantity(BigDecimal.valueOf(9));
        assertThat(service.check(branchId, date, 1, request).dates().getFirst().plannedProduction()).isFalse();
        secondAllocation.setHeldQuantity(BigDecimal.ZERO);
        slot.setBookedCount(slot.getCapacity());
        slot.setPriorityBookedCount(slot.getPriorityCapacity());
        assertThat(service.check(branchId, date, 1, request).dates().getFirst().plannedProduction()).isFalse();
        slot.setBookedCount(0);
        slot.setPriorityBookedCount(0);
        features.setPlannedPickupProduction(false);
        assertThat(service.check(branchId, date, 1, request).dates().getFirst().plannedProduction()).isFalse();
        features.setPlannedPickupProduction(true);
        Clock afterMidnight = Clock.fixed(Instant.parse("2026-09-29T18:31:00Z"), ist);
        var sameDateService = new CartAvailabilityService(features, inventory, validation,
                new SmartOrderingRules(features, settings, afterMidnight), policies, allocations,
                new InventoryAvailabilityService(), slotRepository, settings, afterMidnight, noServiceHours());
        assertThat(sameDateService.check(branchId, date, 1, request).dates().getFirst().plannedProduction()).isFalse();
    }

    @Test
    void menuPreviewKeepsLongerProductHorizonsWithoutWeakeningCartAvailability() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-04T03:00:00Z"), ZoneId.of("Asia/Kolkata"));
        LocalDate today = LocalDate.of(2026, 10, 4), tomorrow = today.plusDays(1);
        Branch branch = new Branch(); branch.setId(10L);
        var breakfast = branchProduct(branch, 101L, 201L, "Breakfast");
        var sweets = branchProduct(branch, 102L, 202L, "Sweets");
        var breakfastPolicy = productionPolicy(breakfast); breakfastPolicy.setBookingHorizonDays(0);
        var sweetsPolicy = productionPolicy(sweets);
        EnhancementProperties features = new EnhancementProperties(); features.setFutureOrderingDays(7);
        InventoryProperties inventory = new InventoryProperties(); inventory.setEnforcementEnabled(true);
        var validation = Mockito.mock(OrderValidationService.class);
        var policies = Mockito.mock(BranchInventoryPolicyRepository.class);
        var allocations = Mockito.mock(InventoryDailyAllocationRepository.class);
        var slots = Mockito.mock(PickupSlotRepository.class);
        var settings = Mockito.mock(BranchPickupSettingsRepository.class);
        when(validation.validatePickupCart(any(), anyList())).thenReturn(List.of(
                new ValidatedOrderItem(breakfast.getProduct(), breakfast, ProductSaleMode.UNIT, 1, null),
                new ValidatedOrderItem(sweets.getProduct(), sweets, ProductSaleMode.UNIT, 1, null)));
        when(policies.findByBranchProductIdIn(anyList())).thenReturn(List.of(breakfastPolicy, sweetsPolicy));
        when(allocations.findByBranchProductIdInAndServiceDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(approved(sweets, tomorrow, 10)));
        when(settings.findByBranchId(10L)).thenReturn(Optional.empty());
        when(slots.findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(any(), any(), any()))
                .thenReturn(List.of(slot(branch, tomorrow, 1000L, LocalTime.NOON, LocalTime.of(13, 0))));
        var service = new CartAvailabilityService(features, inventory, validation,
                new SmartOrderingRules(features, settings, clock), policies, allocations,
                new InventoryAvailabilityService(), slots, settings, clock, noServiceHours());
        var request = List.of(new CreateOrderItemRequest(201L, 1, null), new CreateOrderItemRequest(202L, 1, null));
        assertThat(service.check(10L, today, 8, request).dates()).hasSize(1);
        var preview = service.check(10L, today, 8, request, true);
        assertThat(preview.dates()).hasSize(8);
        var time = preview.dates().get(1).slots().getFirst();
        assertThat(time.slot().remainingCapacity()).isPositive();
        assertThat(time.issues()).extracting(CartAvailabilityService.ItemAvailability::productId).containsExactly(201L);
        assertThat(time.issues().getFirst().code()).isEqualTo("PRODUCT_HORIZON");
        // Neither the menu-preview response nor normal checkout approves a cart containing the breakfast item.
        assertThat(time.normalAvailable()).isFalse();
        assertThat(service.check(10L, tomorrow, 1, request).dates().getFirst().slots().getFirst().normalAvailable()).isFalse();
    }

    private com.gokulsweets.restaurant.menu.MenuServiceWindows noServiceHours() {
        var windows = Mockito.mock(com.gokulsweets.restaurant.menu.MenuServiceWindows.class);
        when(windows.pickupEvaluator(org.mockito.ArgumentMatchers.anyLong())).thenReturn(at ->
                new com.gokulsweets.restaurant.menu.MenuServiceWindows.Snapshot(false, java.util.Map.of()));
        return windows;
    }

    private BranchProduct branchProduct(Branch branch, long id, long productId, String name) {
        var product = new Product(); product.setId(productId); product.setName(name);
        var branchProduct = new BranchProduct(); branchProduct.setId(id);
        branchProduct.setBranch(branch); branchProduct.setProduct(product);
        return branchProduct;
    }

    private BranchInventoryPolicy productionPolicy(BranchProduct branchProduct) {
        var policy = new BranchInventoryPolicy(); policy.setBranchProduct(branchProduct);
        policy.setControlMode(InventoryControlMode.DAILY_PRODUCTION);
        policy.setInventoryUnit(InventoryUnit.PIECE); policy.setOnlineEnabled(true);
        policy.setBookingHorizonDays(7); policy.setProductionLeadMinutes(0);
        return policy;
    }

    private InventoryDailyAllocation approved(BranchProduct branchProduct, LocalDate date, int quantity) {
        var allocation = new InventoryDailyAllocation(); allocation.setBranchProduct(branchProduct);
        allocation.setServiceDate(date); allocation.setInventoryUnit(InventoryUnit.PIECE);
        allocation.setStatus(InventoryAllocationStatus.APPROVED);
        allocation.setApprovedQuantity(BigDecimal.valueOf(quantity));
        return allocation;
    }

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
                clock, noServiceHours()
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

        when(orderValidation.validatePickupCart(any(), anyList()))
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
