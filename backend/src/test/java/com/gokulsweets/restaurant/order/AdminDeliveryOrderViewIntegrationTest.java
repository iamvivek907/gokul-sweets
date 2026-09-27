package com.gokulsweets.restaurant.order;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.order.config.PreparationWindowProperties;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.AdminOrderQueryService;
import com.gokulsweets.restaurant.order.service.PreparationEligibilityService;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class AdminDeliveryOrderViewIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationClock clock;

    @Test
    void staffDetailAndListShowDeliveryWithoutDereferencingPickupSlot() {
        String key = UUID.randomUUID().toString().substring(0, 8);
        Long branchId = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, ?) RETURNING id",
                Long.class, "ADV-" + key, "Branch " + key);
        Long zoneId = jdbc.queryForObject("""
                INSERT INTO delivery_zones(branch_id, locality_key, postal_code, opens_at, closes_at)
                VALUES (?, 'hazratganj', '226001', '10:00', '20:00') RETURNING id
                """, Long.class, branchId);
        Long windowId = jdbc.queryForObject("""
                INSERT INTO delivery_capacity_windows(zone_id, service_date, starts_at, ends_at, rider_capacity)
                VALUES (?, ?, ?, ?, 1) RETURNING id
                """, Long.class, zoneId, LocalDate.of(2026, 10, 1), LocalTime.of(11, 0), LocalTime.of(12, 0));
        var branch = new Branch();
        branch.setId(branchId);
        branch.setName("Branch " + key);
        var order = new Order();
        order.setId(99L);
        order.setOrderNumber("ADV-" + key);
        order.setBranch(branch);
        order.setCustomerName("Customer");
        order.setCustomerPhone("9999999999");
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setDeliveryWindowId(windowId);
        order.setDeliveryAddressLine("12 Main Road");
        order.setDeliveryLocality("Hazratganj");
        order.setDeliveryPostalCode("226001");
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(BigDecimal.TEN);
        var repo = mock(OrderRepository.class);
        when(repo.findDetailedByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(repo.findByBranchId(eq(branchId), any())).thenReturn(new PageImpl<>(List.of(order)));
        var service = new AdminOrderQueryService(repo, mock(PaymentRepository.class),
                mock(StaffAuthorizationService.class), mock(PreparationEligibilityService.class),
                new PreparationWindowProperties(), clock, jdbc);

        var detail = service.getOrder(order.getOrderNumber());
        assertThat(detail.pickupDate()).isNull();
        assertThat(detail.deliveryDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(detail.deliveryAddressLine()).isEqualTo("12 Main Road");
        var summary = service.getOrders(branchId, null, 0, 10).orders().getFirst();
        assertThat(summary.fulfillmentType()).isEqualTo(FulfillmentType.DELIVERY);
        assertThat(summary.deliveryStartTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    void pickupStaffViewDoesNotQueryAWindowWithNullId() {
        var branch = new Branch();
        branch.setId(10L);
        branch.setName("Pickup branch");
        var slot = new PickupSlot();
        slot.setSlotDate(LocalDate.of(2026, 10, 1));
        slot.setStartTime(LocalTime.of(11, 0));
        slot.setEndTime(LocalTime.of(12, 0));
        var order = new Order();
        order.setId(55L);
        order.setOrderNumber("ADV-PICKUP");
        order.setBranch(branch);
        order.setPickupSlot(slot);
        order.setPickupType(PickupType.NORMAL);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        var repo = mock(OrderRepository.class);
        when(repo.findDetailedByOrderNumber("ADV-PICKUP")).thenReturn(Optional.of(order));
        when(repo.findByBranchId(eq(10L), any())).thenReturn(new PageImpl<>(List.of(order)));
        var service = new AdminOrderQueryService(repo, mock(PaymentRepository.class),
                mock(StaffAuthorizationService.class), mock(PreparationEligibilityService.class),
                new PreparationWindowProperties(), clock, jdbc);

        assertThat(service.getOrder("ADV-PICKUP").pickupDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(service.getOrders(10L, null, 0, 10).orders().getFirst().deliveryDate()).isNull();
    }
}
