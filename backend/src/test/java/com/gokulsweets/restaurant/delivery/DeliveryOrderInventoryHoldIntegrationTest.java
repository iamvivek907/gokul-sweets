package com.gokulsweets.restaurant.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.entity.BranchInventoryPolicy;
import com.gokulsweets.restaurant.inventory.entity.InventoryDailyAllocation;
import com.gokulsweets.restaurant.inventory.entity.InventoryReservation;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.enums.InventoryReservationStatus;
import com.gokulsweets.restaurant.inventory.enums.InventoryUnit;
import com.gokulsweets.restaurant.inventory.model.InventoryAvailability;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryDailyAllocationRepository;
import com.gokulsweets.restaurant.inventory.repository.InventoryReservationRepository;
import com.gokulsweets.restaurant.inventory.service.*;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.service.SmartOrderingRules;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderItem;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest
@Transactional
class DeliveryOrderInventoryHoldIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void heldRiderWindowSuppliesIstStockDateAndWrongHoldCannotReserve() {
        Clock ist = Clock.system(ZoneId.of("Asia/Kolkata"));
        LocalDate deliveryDate = LocalDate.now(ist).plusDays(1);
        String key = UUID.randomUUID().toString().substring(0, 8);
        Long branchId =
                jdbc.queryForObject(
                        "INSERT INTO branches(code, name) VALUES (?, ?) RETURNING id",
                        Long.class,
                        "DIH-" + key,
                        "Delivery " + key);
        Long zoneId =
                jdbc.queryForObject(
                        """
INSERT INTO delivery_zones(branch_id, locality_key, postal_code, opens_at, closes_at)
VALUES (?, 'hazratganj', '226001', '10:00', '20:00') RETURNING id
""",
                        Long.class,
                        branchId);
        Long windowId =
                jdbc.queryForObject(
                        """
INSERT INTO delivery_capacity_windows(zone_id, service_date, starts_at, ends_at, rider_capacity, reserved_count)
VALUES (?, ?, '11:00', '12:00', 1, 1) RETURNING id
""",
                        Long.class,
                        zoneId,
                        deliveryDate);
        String holdKey = "inventory-" + key;
        jdbc.update(
                """
INSERT INTO delivery_rider_holds(hold_key, window_id, request_fingerprint, expires_at)
VALUES (?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '20 minutes')
""",
                holdKey,
                windowId,
                "a".repeat(64));

        var branch = new Branch();
        branch.setId(branchId);
        var product = new Product();
        product.setId(12L);
        product.setName("Sweet");
        var branchProduct = new BranchProduct();
        branchProduct.setId(13L);
        branchProduct.setBranch(branch);
        var item = new ValidatedOrderItem(product, branchProduct, ProductSaleMode.UNIT, 1, null);
        var validated = new ValidatedOrderData(branch, null, null, List.of(item));
        var order = new Order();
        order.setOrderNumber("DIH-" + key);
        order.setBranch(branch);
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setDeliveryWindowId(windowId);
        order.setDeliveryHoldKey(holdKey);
        order.setReservationExpiresAt(LocalDateTime.now(ist).plusMinutes(15));

        var allocations = mock(InventoryDailyAllocationRepository.class);
        var reservations = mock(InventoryReservationRepository.class);
        var policies = mock(BranchInventoryPolicyRepository.class);
        var available = mock(InventoryAvailabilityService.class);
        var quantities = mock(InventoryQuantityService.class);
        var allocation = new InventoryDailyAllocation();
        allocation.setBranchProduct(branchProduct);
        allocation.setServiceDate(deliveryDate);
        allocation.setInventoryUnit(InventoryUnit.PIECE);
        allocation.setExpectedReadyAt(deliveryDate.atTime(10, 30));
        allocation.setStatus(InventoryAllocationStatus.APPROVED);
        allocation.setApprovedQuantity(BigDecimal.TEN);
        var policy = new BranchInventoryPolicy();
        policy.setOnlineEnabled(true);
        policy.setControlMode(InventoryControlMode.DAILY_PRODUCTION);
        policy.setInventoryUnit(InventoryUnit.PIECE);
        policy.setBookingHorizonDays(30);
        when(allocations.findForUpdate(13L, deliveryDate)).thenReturn(Optional.of(allocation));
        when(policies.findByBranchProductId(13L)).thenReturn(Optional.of(policy));
        when(quantities.normalizePositive(any(), eq(InventoryUnit.PIECE), anyString()))
                .thenReturn(BigDecimal.ONE);
        when(available.calculate(allocation, policy))
                .thenReturn(
                        new InventoryAvailability(
                                13L,
                                deliveryDate,
                                InventoryUnit.PIECE,
                                InventoryAllocationStatus.APPROVED,
                                BigDecimal.TEN,
                                true,
                                null,
                                null));
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var inventory = new InventoryProperties();
        inventory.setEnforcementEnabled(true);
        var flags = new EnhancementProperties();
        flags.setDeliveryRiderHolds(true);
        flags.setPlannedDeliveryProduction(true);
        var service =
                new OrderInventoryReservationService(
                        inventory,
                        reservations,
                        allocations,
                        policies,
                        available,
                        quantities,
                        mock(InventoryLedgerService.class),
                        ist,
                        flags,
                        mock(SmartOrderingRules.class),
                        jdbc);

        allocation.setExpectedReadyAt(deliveryDate.atTime(11, 30));
        assertThatThrownBy(() -> service.synchronizePendingDeliveryOrder(order, validated))
                .hasMessageContaining("Approved production is not ready");
        verify(allocations).findForUpdate(13L, deliveryDate);
        verify(reservations, never()).save(any(InventoryReservation.class));
        assertThat(allocation.getHeldQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        clearInvocations(allocations);

        allocation.setExpectedReadyAt(deliveryDate.atTime(10, 30));
        flags.setPlannedDeliveryProduction(false);
        assertThatThrownBy(() -> service.synchronizePendingDeliveryOrder(order, validated))
                .hasMessageContaining("Approved production is not ready");
        verify(allocations).findForUpdate(13L, deliveryDate);
        verify(reservations, never()).save(any(InventoryReservation.class));
        assertThat(allocation.getHeldQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        clearInvocations(allocations);
        flags.setPlannedDeliveryProduction(true);

        service.synchronizePendingDeliveryOrder(order, validated);
        assertThat(allocation.getHeldQuantity()).isEqualByComparingTo(BigDecimal.ONE);
        verify(allocations).findForUpdate(13L, deliveryDate);

        var held = new InventoryReservation();
        held.setAllocation(allocation);
        held.setOrderNumber(order.getOrderNumber());
        held.setStatus(InventoryReservationStatus.TEMPORARY_HOLD);
        held.setQuantity(BigDecimal.ONE);
        held.setExpiresAt(order.getReservationExpiresAt());
        when(reservations.findByOrderNumberForUpdate(order.getOrderNumber()))
                .thenReturn(List.of(held));

        // No spare stock is needed when a valid hold already owns the last unit.
        allocation.setApprovedQuantity(BigDecimal.ONE);
        service.synchronizePendingDeliveryOrder(order, validated);
        assertThat(allocation.getHeldQuantity()).isEqualByComparingTo(BigDecimal.ONE);
        for (var status :
                List.of(
                        InventoryAllocationStatus.DRAFT,
                        InventoryAllocationStatus.DELAYED,
                        InventoryAllocationStatus.UNAVAILABLE,
                        InventoryAllocationStatus.CLOSED)) {
            allocation.setStatus(status);
            assertThatThrownBy(() -> service.synchronizePendingDeliveryOrder(order, validated))
                    .hasMessageContaining("Approved production is not ready");
        }
        allocation.setStatus(InventoryAllocationStatus.APPROVED);
        allocation.setApprovedQuantity(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.synchronizePendingDeliveryOrder(order, validated))
                .hasMessageContaining("no longer covers");
        allocation.setApprovedQuantity(BigDecimal.ONE);
        allocation.setWastedQuantity(BigDecimal.ONE);
        assertThatThrownBy(() -> service.synchronizePendingDeliveryOrder(order, validated))
                .hasMessageContaining("no longer covers");
        allocation.setWastedQuantity(BigDecimal.ZERO);
        policy.setReadyStockRequired(true);
        assertThatThrownBy(() -> service.synchronizePendingDeliveryOrder(order, validated))
                .hasMessageContaining("Approved production is not ready");
        allocation.setStatus(InventoryAllocationStatus.READY);
        allocation.setReadyQuantity(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.synchronizePendingDeliveryOrder(order, validated))
                .hasMessageContaining("no longer covers");
        allocation.setReadyQuantity(BigDecimal.ONE);
        service.synchronizePendingDeliveryOrder(order, validated);
        assertThat(allocation.getHeldQuantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(held.getStatus()).isEqualTo(InventoryReservationStatus.TEMPORARY_HOLD);

        order.setDeliveryHoldKey("another-hold");
        assertThatThrownBy(() -> service.synchronizePendingDeliveryOrder(order, validated))
                .hasMessageContaining("no longer reserved");
        verify(reservations, times(1)).save(any(InventoryReservation.class));
    }
}
