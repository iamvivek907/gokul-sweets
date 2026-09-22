package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.*;
import com.gokulsweets.restaurant.inventory.enums.*;
import com.gokulsweets.restaurant.inventory.repository.*;
import com.gokulsweets.restaurant.inventory.service.InventoryAvailabilityService;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.pickup.BranchPickupSettingsRepository;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;
import com.gokulsweets.restaurant.product.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartAvailabilityServiceTest {
    @Test void weightHoldsAndFullSlotsProduceSelectableAlternativeDate() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T04:30:00Z"), ZoneId.of("Asia/Kolkata"));
        LocalDate today = LocalDate.now(clock);
        var features = new EnhancementProperties();
        var inventory = new InventoryProperties(); inventory.setEnforcementEnabled(true);
        var validation = mock(OrderValidationService.class);
        var policies = mock(BranchInventoryPolicyRepository.class);
        var allocations = mock(InventoryDailyAllocationRepository.class);
        var slots = mock(PickupSlotRepository.class);
        var settings = mock(BranchPickupSettingsRepository.class);
        var product = new Product(); product.setId(11L); product.setName("Test sweet");
        var bp = new BranchProduct(); bp.setId(22L); bp.setProduct(product);
        var policy = new BranchInventoryPolicy(); policy.setBranchProduct(bp); policy.setOnlineEnabled(true);
        policy.setInventoryUnit(InventoryUnit.GRAM); policy.setControlMode(InventoryControlMode.DAILY_PRODUCTION); policy.setBookingHorizonDays(30);
        var todayStock = stock(bp, today); todayStock.setHeldQuantity(BigDecimal.valueOf(300));
        var tomorrowStock = stock(bp, today.plusDays(1));
        var first = SmartOrderingRulesTest.slot(today, LocalTime.NOON);
        var full = SmartOrderingRulesTest.slot(today.plusDays(1), LocalTime.NOON); full.setBookedCount(5);
        var available = SmartOrderingRulesTest.slot(today.plusDays(1), LocalTime.of(13, 0)); available.setId(3L);
        var request = List.of(new CreateOrderItemRequest(11L, null, 250));
        when(validation.validateCart(1L, request)).thenReturn(List.of(new ValidatedOrderItem(product, bp, ProductSaleMode.WEIGHT, 1, 250)));
        when(policies.findByBranchProductIdIn(List.of(22L))).thenReturn(List.of(policy));
        when(allocations.findByBranchProductIdInAndServiceDateBetween(List.of(22L), today, today.plusDays(1))).thenReturn(List.of(todayStock, tomorrowStock));
        when(slots.findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(1L, today, today.plusDays(1))).thenReturn(List.of(first, full, available));
        var service = new CartAvailabilityService(features, inventory, validation, new SmartOrderingRules(features, settings, clock),
                policies, allocations, new InventoryAvailabilityService(), slots, settings, clock);
        var result = service.check(1L, today, 2, request);
        assertThat(result.fulfilmentType()).isEqualTo("PICKUP");
        assertThat(result.dates().get(0).available()).isFalse();
        assertThat(result.dates().get(1).available()).isTrue();
        assertThat(result.dates().get(1).slots().get(0).normalAvailable()).isFalse();
        assertThat(result.dates().get(1).slots().get(1).slot().id()).isEqualTo(3L);
        verify(validation).validateCart(1L, request);
    }

    private InventoryDailyAllocation stock(BranchProduct bp, LocalDate date) {
        var result = new InventoryDailyAllocation(); result.setBranchProduct(bp); result.setServiceDate(date);
        result.setInventoryUnit(InventoryUnit.GRAM); result.setStatus(InventoryAllocationStatus.APPROVED);
        result.setApprovedQuantity(BigDecimal.valueOf(500));
        return result;
    }
}
