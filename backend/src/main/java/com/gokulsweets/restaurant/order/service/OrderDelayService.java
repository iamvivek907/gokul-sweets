package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderDetailResponse;
import com.gokulsweets.restaurant.order.dto.admin.UpdateOrderDelayRequest;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;

/** Coordinates order delay operations. */
@Service
@RequiredArgsConstructor
public class OrderDelayService {

    private static final EnumSet<OrderStatus> REPORTABLE =
            EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING);

    private final EnhancementProperties features;

    private final ApplicationClock clock;

    private final OrderRepository orders;

    private final StaffAuthorizationService authorization;

    private final AdminOrderQueryService queries;

    private final com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox
            notifications;

    /**
     * Returns report information for order delay.
     *
     * <p>Authorization checks include {@code PermissionName.ORDER_MARK_READY}.
     *
     * @param orderNumber the order number supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code queries.getOrder(orderNumber)}
     * @throws IllegalArgumentException when the method rejects the request with {@code Provide a
     *     revised ready time and a reason of 10 to 300 characters.}; {@code The revised ready time
     *     must be after the booked pickup start and within 24 hours from now (IST).}
     * @throws IllegalStateException when the method rejects the request with {@code Delay reporting
     *     is not enabled.}; {@code Delivery readiness updates require a revised rider window.};
     *     {@code Only confirmed or preparing orders can report a revised ready time.}
     */
    @Transactional
    public AdminOrderDetailResponse report(String orderNumber, UpdateOrderDelayRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderDelayService.class, "report(String,UpdateOrderDelayRequest)");
        try {
            if (!features.isTruthfulOrderTracking()) {
                throw new IllegalStateException("Delay reporting is not enabled.");
            }
            Order order =
                    orders.findForUpdate(orderNumber)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("Order does not exist."));
            authorization.requireBranchAccess(order.getBranch().getId());
            authorization.requirePermission(PermissionName.ORDER_MARK_READY);
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY)
                throw new IllegalStateException(
                        "Delivery readiness updates require a revised rider window.");
            if (!REPORTABLE.contains(order.getOrderStatus())) {
                throw new IllegalStateException(
                        "Only confirmed or preparing orders can report a revised ready time.");
            }
            if (request.estimatedReadyAt() == null
                    || request.reason() == null
                    || request.reason().trim().length() < 10
                    || request.reason().trim().length() > 300) {
                throw new IllegalArgumentException(
                        "Provide a revised ready time and a reason of 10 to 300 characters.");
            }
            LocalDateTime now = clock.now();
            LocalDateTime originalStart =
                    order.getPickupSlot()
                            .getSlotDate()
                            .atTime(order.getPickupSlot().getStartTime());
            if (!request.estimatedReadyAt().isAfter(now)
                    || !request.estimatedReadyAt().isAfter(originalStart)
                    || request.estimatedReadyAt().isAfter(now.plusHours(24))) {
                throw new IllegalArgumentException(
                        "The revised ready time must be after the booked pickup start and within 24"
                                + " hours from now (IST).");
            }
            String reason = request.reason().trim();
            if (!request.estimatedReadyAt().equals(order.getEstimatedReadyAt())
                    || !reason.equals(order.getDelayReason())) {
                order.setEstimatedReadyAt(request.estimatedReadyAt());
                order.setDelayReason(reason);
                order.setDelayReportedAt(now);
                orders.saveAndFlush(order);
                notifications.delayChanged(order.getId());
            }
            return queries.getOrder(orderNumber);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderDelayService.class,
                    "report(String,UpdateOrderDelayRequest)");
        }
    }
}
