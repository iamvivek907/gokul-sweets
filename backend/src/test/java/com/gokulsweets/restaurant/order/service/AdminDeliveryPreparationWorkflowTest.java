package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.kot.entity.Kot;
import com.gokulsweets.restaurant.kot.service.KotService;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PreparationEligibilityStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class AdminDeliveryPreparationWorkflowTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final AdminOrderQueryService queries = mock(AdminOrderQueryService.class);
    private final StaffAuthorizationService staff = mock(StaffAuthorizationService.class);
    private final KotService kots = mock(KotService.class);
    private final PreparationEligibilityService eligibility = mock(PreparationEligibilityService.class);
    private final com.gokulsweets.restaurant.delivery.DeliveryDispatchPilotService dispatch = mock(com.gokulsweets.restaurant.delivery.DeliveryDispatchPilotService.class);
    private final com.gokulsweets.restaurant.occasion.OccasionProductionReadinessService bulkReadiness = mock(com.gokulsweets.restaurant.occasion.OccasionProductionReadinessService.class);
    private final AdminOrderWorkflowService workflow = new AdminOrderWorkflowService(
            orders, queries, staff, kots, eligibility, dispatch, bulkReadiness);
    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(42L);
        order.setOrderNumber("GKS-DELIVERY-42");
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        var branch = new Branch();
        branch.setId(7L);
        order.setBranch(branch);
        when(orders.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
    }

    @Test
    void incompleteBulkProductionCannotBypassReadinessThroughStandardOrderTransition() {
        order.setFulfillmentType(FulfillmentType.PICKUP);
        order.setOrderStatus(OrderStatus.PREPARING);
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT))
                .when(bulkReadiness).requireReady(42L);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> workflow.transitionStatus(order.getOrderNumber(), OrderStatus.READY_FOR_PICKUP));
        verify(staff).requirePermission(PermissionName.ORDER_MARK_READY);
        verify(orders, never()).transitionStatus(anyLong(), any(), any());
    }

    @Test
    void eligibleDeliveryStartsPreparationAndCreatesKot() {
        when(eligibility.evaluate(order)).thenReturn(new PreparationEligibility(
                PreparationEligibilityStatus.ELIGIBLE,
                LocalDateTime.of(2026, 9, 27, 19, 30),
                LocalDateTime.of(2026, 9, 27, 18, 30), 30));
        when(orders.transitionStatus(42L, OrderStatus.CONFIRMED, OrderStatus.PREPARING)).thenReturn(1);
        when(orders.findDetailedByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(kots.getOrCreateForPreparation(order)).thenReturn(new Kot());

        workflow.transitionStatus(order.getOrderNumber(), OrderStatus.PREPARING);

        verify(staff).requireBranchAccess(7L);
        verify(staff).requirePermission(PermissionName.ORDER_START_PREPARATION);
        verify(orders).transitionStatus(42L, OrderStatus.CONFIRMED, OrderStatus.PREPARING);
        verify(kots).getOrCreateForPreparation(order);
    }

    @Test
    void earlyDeliveryCannotStartPreparation() {
        when(eligibility.evaluate(order)).thenReturn(new PreparationEligibility(
                PreparationEligibilityStatus.SCHEDULED,
                LocalDateTime.of(2026, 9, 27, 19, 30),
                LocalDateTime.of(2026, 9, 27, 18, 30), 90));

        assertThrows(IllegalStateException.class,
                () -> workflow.transitionStatus(order.getOrderNumber(), OrderStatus.PREPARING));
        verify(orders, never()).transitionStatus(anyLong(), any(), any());
        verifyNoInteractions(kots);
    }

    @Test
    void deliveryCannotUsePickupReadyOrCollectedStatuses() {
        order.setOrderStatus(OrderStatus.PREPARING);

        assertThrows(IllegalStateException.class,
                () -> workflow.transitionStatus(order.getOrderNumber(), OrderStatus.READY_FOR_PICKUP));
        order.setOrderStatus(OrderStatus.READY_FOR_PICKUP);
        assertThrows(IllegalStateException.class,
                () -> workflow.transitionStatus(order.getOrderNumber(), OrderStatus.PICKED_UP));
        verify(orders, never()).transitionStatus(anyLong(), any(), any());
        verifyNoInteractions(kots);
    }

    @Test
    void deliveryHandoffRequiresEachPermissionAndCannotSkipDispatch() {
        order.setOrderStatus(OrderStatus.PREPARING);
        assertThrows(IllegalStateException.class,
                () -> workflow.transitionStatus(order.getOrderNumber(), OrderStatus.DELIVERED));
        when(orders.transitionStatus(42L, OrderStatus.PREPARING, OrderStatus.READY_FOR_DELIVERY))
                .thenReturn(1);
        when(orders.transitionStatus(42L, OrderStatus.READY_FOR_DELIVERY, OrderStatus.OUT_FOR_DELIVERY))
                .thenReturn(1);
        when(orders.transitionStatus(42L, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED))
                .thenReturn(1);

        workflow.transitionStatus(order.getOrderNumber(), OrderStatus.READY_FOR_DELIVERY);
        verify(staff).requirePermission(PermissionName.ORDER_MARK_READY);
        order.setOrderStatus(OrderStatus.READY_FOR_DELIVERY);
        workflow.transitionStatus(order.getOrderNumber(), OrderStatus.OUT_FOR_DELIVERY);
        verify(staff).requirePermission(PermissionName.ORDER_DISPATCH_DELIVERY);
        order.setOrderStatus(OrderStatus.OUT_FOR_DELIVERY);
        workflow.transitionStatus(order.getOrderNumber(), OrderStatus.DELIVERED);
        verify(staff).requirePermission(PermissionName.ORDER_CONFIRM_DELIVERY);
        verify(staff, times(4)).requireBranchAccess(7L);
        verifyNoInteractions(kots);

        order.setOrderStatus(OrderStatus.DELIVERED);
        assertDoesNotThrow(() -> workflow.transitionStatus(order.getOrderNumber(), OrderStatus.DELIVERED));
        verify(orders, times(3)).transitionStatus(anyLong(), any(), any());
    }

    @Test
    void deniedDeliveryPermissionDoesNotChangeStatus() {
        order.setOrderStatus(OrderStatus.READY_FOR_DELIVERY);
        doThrow(new org.springframework.security.access.AccessDeniedException("Denied"))
                .when(staff).requirePermission(PermissionName.ORDER_DISPATCH_DELIVERY);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> workflow.transitionStatus(order.getOrderNumber(), OrderStatus.OUT_FOR_DELIVERY));
        verify(orders, never()).transitionStatus(anyLong(), any(), any());
    }
}
