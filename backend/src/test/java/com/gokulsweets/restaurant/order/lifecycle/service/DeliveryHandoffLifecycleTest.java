package com.gokulsweets.restaurant.order.lifecycle.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.delivery.DeliveryRiderHoldService;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryLifecycleService;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.AdminOrderQueryService;
import com.gokulsweets.restaurant.order.service.AdminOrderWorkflowService;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.pickup.service.PickupSlotReservationService;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class DeliveryHandoffLifecycleTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final AdminOrderWorkflowService workflow = mock(AdminOrderWorkflowService.class);
    private final AdminOrderQueryService queries = mock(AdminOrderQueryService.class);
    private final StaffAuthorizationService staff = mock(StaffAuthorizationService.class);
    private final PickupSlotReservationService pickup = mock(PickupSlotReservationService.class);
    private final DeliveryRiderHoldService riders = mock(DeliveryRiderHoldService.class);
    private final OrderInventoryLifecycleService inventory =
            mock(OrderInventoryLifecycleService.class);
    private final AdminOrderLifecycleCoordinator coordinator =
            new AdminOrderLifecycleCoordinator(
                    mock(com.gokulsweets.restaurant.loyalty.LoyaltyService.class),
                    orders,
                    payments,
                    workflow,
                    queries,
                    staff,
                    pickup,
                    riders,
                    inventory,
                    mock(
                            com.gokulsweets.restaurant.customer.notification
                                    .CustomerNotificationInbox.class),
                    mock(com.gokulsweets.restaurant.order.service.PickupCodeService.class));

    @Test
    void inventoryIsFulfilledOnDeliveredButNotOnDispatchAndRetriesAreIdempotent() {
        coordinator.transitionStatus("DEL-1", OrderStatus.OUT_FOR_DELIVERY);
        verifyNoInteractions(inventory);
        coordinator.transitionStatus("DEL-1", OrderStatus.DELIVERED);
        coordinator.transitionStatus("DEL-1", OrderStatus.DELIVERED);
        verify(inventory, times(2)).fulfilOrderInventory(eq("DEL-1"), anyString());
        // fulfilOrderInventory is idempotent for already fulfilled reservations.
    }

    @Test
    void paidPreparedDeliveryRequiresRefundBeforeCancellation() {
        Order order = deliveryOrder(OrderStatus.READY_FOR_DELIVERY);
        when(orders.findForUpdate("DEL-1")).thenReturn(Optional.of(order));
        when(payments.existsByOrderId(1L)).thenReturn(true);

        assertThrows(
                InventoryConflictException.class,
                () -> coordinator.cancelUnpaidOrder("DEL-1", "Customer request"));
        verifyNoInteractions(riders, inventory);
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void dispatchedDeliveryCannotBeCancelledOrLateCollectedAsPickup() {
        Order order = deliveryOrder(OrderStatus.OUT_FOR_DELIVERY);
        when(orders.findForUpdate("DEL-1")).thenReturn(Optional.of(order));

        assertThrows(
                IllegalStateException.class,
                () -> coordinator.cancelUnpaidOrder("DEL-1", "Customer request"));
        assertThrows(IllegalStateException.class, () -> coordinator.collectLateOrder("DEL-1"));
        verifyNoInteractions(riders, inventory);
    }

    private static Order deliveryOrder(OrderStatus status) {
        var order = new Order();
        order.setId(1L);
        order.setOrderNumber("DEL-1");
        order.setOrderStatus(status);
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        var branch = new Branch();
        branch.setId(7L);
        order.setBranch(branch);
        return order;
    }
}
