package com.gokulsweets.restaurant.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryAvailabilityService;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.OrderValidationService;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

class DeliveryStockCheckTest {
    private static final Clock IST =
            Clock.fixed(Instant.parse("2026-09-27T18:35:00Z"), ZoneId.of("Asia/Kolkata"));

    @Test
    void verifiesRequestedQuantityUsingCurrentAllocationAndFailsClosed() {
        var inventory = new InventoryProperties();
        inventory.setEnforcementEnabled(true);
        var validation = mock(OrderValidationService.class);
        var policies = mock(BranchInventoryPolicyRepository.class);
        var allocations = mock(InventoryDailyAllocationRepository.class);
        var availability = mock(InventoryAvailabilityService.class);
        var service =
                new DeliveryStockCheck(
                        inventory, validation, policies, allocations, availability, IST);
        var date = LocalDate.of(2026, 9, 28);
        var request = List.of(new CreateOrderItemRequest(5L, 3, null));
        var product = new Product();
        product.setId(5L);
        var branchProduct = new BranchProduct();
        branchProduct.setId(8L);
        when(validation.validateCart(2L, request))
                .thenReturn(
                        List.of(
                                new ValidatedOrderItem(
                                        product, branchProduct, ProductSaleMode.UNIT, 3, null)));
        var policy = new BranchInventoryPolicy();
        policy.setBranchProduct(branchProduct);
        policy.setOnlineEnabled(true);
        policy.setControlMode(InventoryControlMode.DAILY_PRODUCTION);
        policy.setInventoryUnit(InventoryUnit.PIECE);
        policy.setBookingHorizonDays(7);
        var allocation = mock(InventoryDailyAllocation.class);
        when(allocation.getBranchProduct()).thenReturn(branchProduct);
        when(allocation.getInventoryUnit()).thenReturn(InventoryUnit.PIECE);
        when(policies.findByBranchProductIdIn(List.of(8L))).thenReturn(List.of(policy));
        when(allocations.findByBranchProductIdInAndServiceDate(List.of(8L), date))
                .thenReturn(List.of(allocation));
        when(availability.calculate(eq(allocation), eq(policy)))
                .thenReturn(
                        new InventoryAvailability(
                                8L,
                                date,
                                InventoryUnit.PIECE,
                                InventoryAllocationStatus.APPROVED,
                                BigDecimal.valueOf(2),
                                true,
                                null,
                                null));

        assertThat(service.check(2L, date, request).available()).isFalse();
        when(availability.calculate(eq(allocation), eq(policy)))
                .thenReturn(
                        new InventoryAvailability(
                                8L,
                                date,
                                InventoryUnit.PIECE,
                                InventoryAllocationStatus.APPROVED,
                                BigDecimal.valueOf(3),
                                true,
                                null,
                                null));
        assertThat(service.check(2L, date, request).available()).isTrue();
        when(allocations.findByBranchProductIdInAndServiceDate(List.of(8L), date))
                .thenReturn(List.of());
        assertThat(service.check(2L, date, request).available()).isFalse();
        inventory.setEnforcementEnabled(false);
        assertThat(service.check(2L, date, request).available()).isFalse();
        verify(availability, times(2)).calculate(any(), any());
    }

    @Test
    void futureWindowRequiresApprovedProductionReadyByItsStartAndLeadTime() {
        var inventory = new InventoryProperties();
        inventory.setEnforcementEnabled(true);
        var validation = mock(OrderValidationService.class);
        var policies = mock(BranchInventoryPolicyRepository.class);
        var allocations = mock(InventoryDailyAllocationRepository.class);
        var check =
                new DeliveryStockCheck(
                        inventory,
                        validation,
                        policies,
                        allocations,
                        new InventoryAvailabilityService(),
                        IST);
        var date = LocalDate.of(2026, 9, 28);
        var requested = List.of(new CreateOrderItemRequest(5L, 1, null));
        var product = new Product();
        product.setId(5L);
        var branchProduct = new BranchProduct();
        branchProduct.setId(8L);
        when(validation.validateCart(2L, requested))
                .thenReturn(
                        List.of(
                                new ValidatedOrderItem(
                                        product, branchProduct, ProductSaleMode.UNIT, 1, null)));
        var policy = new BranchInventoryPolicy();
        policy.setBranchProduct(branchProduct);
        policy.setOnlineEnabled(true);
        policy.setControlMode(InventoryControlMode.DAILY_PRODUCTION);
        policy.setInventoryUnit(InventoryUnit.PIECE);
        policy.setBookingHorizonDays(7);
        policy.setProductionLeadMinutes(90);
        var allocation = new InventoryDailyAllocation();
        allocation.setBranchProduct(branchProduct);
        allocation.setServiceDate(date);
        allocation.setInventoryUnit(InventoryUnit.PIECE);
        allocation.setStatus(InventoryAllocationStatus.APPROVED);
        allocation.setApprovedQuantity(BigDecimal.TEN);
        allocation.setExpectedReadyAt(LocalDateTime.of(2026, 9, 28, 11, 30));
        when(policies.findByBranchProductIdIn(List.of(8L))).thenReturn(List.of(policy));
        when(allocations.findByBranchProductIdInAndServiceDate(List.of(8L), date))
                .thenReturn(List.of(allocation));
        assertThat(check.checkWindow(2L, date, LocalTime.of(11, 0), requested).available())
                .isFalse();
        assertThat(check.checkWindow(2L, date, LocalTime.of(12, 0), requested).available())
                .isTrue();
        allocation.setStatus(InventoryAllocationStatus.DRAFT);
        allocation.setForecastQuantity(BigDecimal.valueOf(100));
        assertThat(check.checkWindow(2L, date, LocalTime.of(12, 0), requested).available())
                .isFalse();
        allocation.setStatus(InventoryAllocationStatus.APPROVED);
        allocation.setHeldQuantity(BigDecimal.TEN);
        assertThat(check.checkWindow(2L, date, LocalTime.of(12, 0), requested).available())
                .isFalse();
        allocation.setHeldQuantity(BigDecimal.ZERO);
        policy.setProductionLeadMinutes(800);
        assertThat(check.checkWindow(2L, date, LocalTime.of(12, 0), requested).available())
                .isFalse();
    }
}
