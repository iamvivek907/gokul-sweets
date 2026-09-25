package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.dto.admin.UpdateOrderDelayRequest;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.pickup.PickupSlot;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderDelayServiceTest {
    private final EnhancementProperties features = new EnhancementProperties();
    private final ApplicationClock clock = mock(ApplicationClock.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final StaffAuthorizationService authorization = mock(StaffAuthorizationService.class);
    private final AdminOrderQueryService queries = mock(AdminOrderQueryService.class);
    private final OrderDelayService service = new OrderDelayService(features, clock, orders, authorization, queries);
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 26, 10, 0);
    private final Order order = new Order();

    @BeforeEach
    void setup() {
        features.setTruthfulOrderTracking(true);
        when(clock.now()).thenReturn(now);
        when(orders.findForUpdate("GKS-TEST")).thenReturn(Optional.of(order));
        Branch branch = new Branch();
        branch.setId(5L);
        order.setBranch(branch);
        PickupSlot slot = new PickupSlot();
        slot.setSlotDate(LocalDate.of(2026, 9, 26));
        slot.setStartTime(LocalTime.of(11, 0));
        order.setPickupSlot(slot);
        order.setOrderStatus(OrderStatus.PREPARING);
    }

    @Test
    void savesIstEstimateOnceAndRetainsTimestampOnRetry() {
        var request = new UpdateOrderDelayRequest(now.plusHours(2), "Kitchen running behind schedule");
        service.report("GKS-TEST", request);
        assertThat(order.getEstimatedReadyAt()).isEqualTo(now.plusHours(2));
        assertThat(order.getDelayReportedAt()).isEqualTo(now);
        verify(authorization).requireBranchAccess(5L);
        verify(authorization).requirePermission(PermissionName.ORDER_MARK_READY);
        service.report("GKS-TEST", request);
        verify(orders, times(1)).saveAndFlush(order);
    }

    @Test
    void blocksDisabledUnauthorizedTerminalAndUnreasonableEstimates() {
        var request = new UpdateOrderDelayRequest(now.plusHours(2), "Kitchen running behind schedule");
        features.setTruthfulOrderTracking(false);
        assertThatThrownBy(() -> service.report("GKS-TEST", request)).isInstanceOf(IllegalStateException.class);
        verify(orders, never()).saveAndFlush(order);

        features.setTruthfulOrderTracking(true);
        doThrow(new SecurityException("wrong branch")).when(authorization).requireBranchAccess(5L);
        assertThatThrownBy(() -> service.report("GKS-TEST", request)).isInstanceOf(SecurityException.class);
        reset(authorization);
        order.setOrderStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.report("GKS-TEST", request)).isInstanceOf(IllegalStateException.class);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> service.report("GKS-TEST",
                new UpdateOrderDelayRequest(now.plusMinutes(30), request.reason())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.report("GKS-TEST",
                new UpdateOrderDelayRequest(now.plusHours(25), request.reason())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(orders, never()).saveAndFlush(order);
    }
}
