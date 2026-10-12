package com.gokulsweets.restaurant.order.lifecycle.service;

import com.gokulsweets.restaurant.delivery.DeliveryRiderHoldService;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryLifecycleService;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderDetailResponse;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.AdminOrderQueryService;
import com.gokulsweets.restaurant.order.service.AdminOrderWorkflowService;
import com.gokulsweets.restaurant.order.service.PickupCodeRejectedException;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.pickup.service.PickupSlotReservationService;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/** Backend admin order lifecycle coordinator contract and implementation. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOrderLifecycleCoordinator {

    private static final EnumSet<OrderStatus> CANCELLABLE_WITHOUT_PAYMENT =
            EnumSet.of(
                    OrderStatus.PENDING_PAYMENT,
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_PICKUP,
                    OrderStatus.READY_FOR_DELIVERY,
                    OrderStatus.PICKUP_WINDOW_EXPIRED);

    private final com.gokulsweets.restaurant.loyalty.LoyaltyService loyalty;

    private final OrderRepository orderRepository;

    private final PaymentRepository paymentRepository;

    private final AdminOrderWorkflowService workflowService;

    private final AdminOrderQueryService queryService;

    private final StaffAuthorizationService authorizationService;

    private final PickupSlotReservationService pickupSlotReservationService;

    private final DeliveryRiderHoldService deliveryRiderHolds;

    private final OrderInventoryLifecycleService inventoryLifecycleService;

    private final com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox
            notifications;

    private final com.gokulsweets.restaurant.order.service.PickupCodeService pickupCodes;

    /**
     * Transitions status.
     *
     * @param orderNumber the order number
     * @param targetStatus the target status
     * @return the transition status result
     */
    @Transactional(noRollbackFor = PickupCodeRejectedException.class)
    public AdminOrderDetailResponse transitionStatus(String orderNumber, OrderStatus targetStatus) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderLifecycleCoordinator.class,
                        "transitionStatus(String,OrderStatus)");
        try {
            return transitionStatus(orderNumber, targetStatus, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderLifecycleCoordinator.class,
                    "transitionStatus(String,OrderStatus)");
        }
    }

    /**
     * Transitions status.
     *
     * @param orderNumber the order number
     * @param targetStatus the target status
     * @param pickupCode the pickup code
     * @return the transition status result
     */
    @Transactional(noRollbackFor = PickupCodeRejectedException.class)
    public AdminOrderDetailResponse transitionStatus(
            String orderNumber, OrderStatus targetStatus, String pickupCode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderLifecycleCoordinator.class,
                        "transitionStatus(String,OrderStatus,String)");
        try {
            AdminOrderDetailResponse response =
                    workflowService.transitionStatus(orderNumber, targetStatus, pickupCode);
            if (targetStatus == OrderStatus.PICKED_UP || targetStatus == OrderStatus.DELIVERED) {
                inventoryLifecycleService.fulfilOrderInventory(orderNumber, currentActor());
            }
            return response;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderLifecycleCoordinator.class,
                    "transitionStatus(String,OrderStatus,String)");
        }
    }

    /**
     * Cancels unpaid order.
     *
     * @param orderNumber the order number
     * @param reason the reason
     * @return the cancel unpaid order result
     */
    @Transactional
    public AdminOrderDetailResponse cancelUnpaidOrder(String orderNumber, String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderLifecycleCoordinator.class, "cancelUnpaidOrder(String,String)");
        try {
            Order order =
                    orderRepository
                            .findForUpdate(orderNumber)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Order does not exist."));
            authorizationService.requireBranchAccess(order.getBranch().getId());
            authorizationService.requirePermission(PermissionName.ORDER_CANCEL);
            if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                return queryService.getOrder(orderNumber);
            }
            if (!CANCELLABLE_WITHOUT_PAYMENT.contains(order.getOrderStatus())) {
                throw new IllegalStateException(
                        "Order cannot be cancelled from " + order.getOrderStatus() + ".");
            }
            /*
             * Financial safety boundary: once any payment attempt exists,
             * cancellation must be coordinated by the future refund module.
             * Never release paid inventory while money remains captured.
             */
            if (paymentRepository.existsByOrderId(order.getId())) {
                throw new InventoryConflictException(
                        "ORDER_REFUND_REQUIRED",
                        "This order has a payment record. Complete the refund workflow before"
                                + " cancelling it.");
            }
            OrderStatus previousStatus = order.getOrderStatus();
            releaseCapacity(order);
            inventoryLifecycleService.cancelOrderInventory(orderNumber, reason, currentActor());
            order.setOrderStatus(OrderStatus.CANCELLED);
            orderRepository.saveAndFlush(order);
            notifications.orderReady(order.getId());
            log.info(
                    "Unpaid order cancelled by admin: orderNumber={}, previousStatus={}, actor={}",
                    orderNumber,
                    previousStatus,
                    currentActor());
            return queryService.getOrder(orderNumber);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderLifecycleCoordinator.class,
                    "cancelUnpaidOrder(String,String)");
        }
    }

    /**
     * Collects late order.
     *
     * @param orderNumber the order number
     * @return the collect late order result
     */
    @Transactional(noRollbackFor = PickupCodeRejectedException.class)
    public AdminOrderDetailResponse collectLateOrder(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderLifecycleCoordinator.class, "collectLateOrder(String)");
        try {
            return collectLateOrder(orderNumber, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderLifecycleCoordinator.class,
                    "collectLateOrder(String)");
        }
    }

    /**
     * Collects late order.
     *
     * @param orderNumber the order number
     * @param pickupCode the pickup code
     * @return the collect late order result
     */
    @Transactional(noRollbackFor = PickupCodeRejectedException.class)
    public AdminOrderDetailResponse collectLateOrder(String orderNumber, String pickupCode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderLifecycleCoordinator.class, "collectLateOrder(String,String)");
        try {
            Order order =
                    orderRepository
                            .findForUpdate(orderNumber)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Order does not exist."));
            authorizationService.requireBranchAccess(order.getBranch().getId());
            authorizationService.requirePermission(PermissionName.ORDER_MARK_PICKED_UP);
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY) {
                throw new IllegalStateException(
                        "Late collection is only available for pickup orders.");
            }
            if (order.getOrderStatus() == OrderStatus.PICKED_UP) {
                inventoryLifecycleService.fulfilOrderInventory(orderNumber, currentActor());
                loyalty.reconcile(order.getId());
                return queryService.getOrder(orderNumber);
            }
            if (order.getOrderStatus() != OrderStatus.PICKUP_WINDOW_EXPIRED) {
                throw new IllegalStateException(
                        "Only an expired pickup-window order can use late collection.");
            }
            pickupCodes.verifyAndConsume(order, pickupCode);
            inventoryLifecycleService.fulfilOrderInventory(orderNumber, currentActor());
            order.setOrderStatus(OrderStatus.PICKED_UP);
            orderRepository.saveAndFlush(order);
            loyalty.reconcile(order.getId());
            notifications.orderReady(order.getId());
            log.info("Late order collected: orderNumber={}, actor={}", orderNumber, currentActor());
            return queryService.getOrder(orderNumber);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderLifecycleCoordinator.class,
                    "collectLateOrder(String,String)");
        }
    }

    /**
     * Releases capacity.
     *
     * @param order the order
     */
    private void releaseCapacity(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminOrderLifecycleCoordinator.class, "releaseCapacity(Order)");
        try {
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY) {
                if (!deliveryRiderHolds.release(order.getDeliveryHoldKey()))
                    throw new IllegalStateException(
                            "Delivery rider reservation is missing during cancellation.");
                return;
            }
            Long slotId = order.getPickupSlot().getId();
            switch (order.getPickupType()) {
                case NORMAL -> pickupSlotReservationService.releaseNormalCapacity(slotId);
                case PRIORITY -> pickupSlotReservationService.releasePriorityCapacity(slotId);
                case ADMIN_OVERRIDE ->
                        log.warn(
                                "Cancelled customer order unexpectedly uses ADMIN_OVERRIDE:"
                                        + " orderNumber={}",
                                order.getOrderNumber());
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderLifecycleCoordinator.class,
                    "releaseCapacity(Order)");
        }
    }

    /**
     * Currents actor.
     *
     * @return the current actor result
     */
    private String currentActor() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminOrderLifecycleCoordinator.class, "currentActor()");
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication == null || authentication.getName() == null
                    ? "admin"
                    : authentication.getName();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderLifecycleCoordinator.class,
                    "currentActor()");
        }
    }
}
