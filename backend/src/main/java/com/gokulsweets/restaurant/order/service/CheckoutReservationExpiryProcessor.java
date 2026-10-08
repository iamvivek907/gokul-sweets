package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.delivery.DeliveryRiderHoldService;
import com.gokulsweets.restaurant.inventory.service.OrderInventoryReservationService;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.pickup.service.PickupSlotReservationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Backend checkout reservation expiry processor contract and implementation. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutReservationExpiryProcessor {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;

    private final PaymentRepository paymentRepository;

    private final PickupSlotReservationService pickupSlotReservationService;

    private final OrderInventoryReservationService orderInventoryReservationService;

    private final DeliveryRiderHoldService deliveryRiderHolds;

    /**
     * Expires reservation.
     *
     * @param orderNumber the order number
     * @return the expire reservation result
     */
    @Transactional
    public boolean expireReservation(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CheckoutReservationExpiryProcessor.class, "expireReservation(String)");
        try {
            Order order = orderRepository.findForUpdate(orderNumber).orElse(null);
            if (order == null) {
                return false;
            }
            if (order.getOrderStatus() != OrderStatus.PENDING_PAYMENT) {
                return false;
            }
            LocalDateTime expiresAt = order.getReservationExpiresAt();
            if (expiresAt == null) {
                log.warn(
                        "Pending order has no reservation expiry: orderId={}, orderNumber={}",
                        order.getId(),
                        order.getOrderNumber());
                return false;
            }
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            if (expiresAt.isAfter(now)) {
                return false;
            }
            /*
             * Once payment creation has started, the payment
             * lifecycle owns the final outcome. Neither pickup nor
             * product inventory may be released independently here.
             */
            if (paymentRepository.existsByOrderId(order.getId())) {
                log.debug(
                        "Skipping checkout reservation expiry because payment exists: orderId={},"
                                + " orderNumber={}",
                        order.getId(),
                        order.getOrderNumber());
                return false;
            }
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY) {
                if (!deliveryRiderHolds.release(order.getDeliveryHoldKey()))
                    throw new IllegalStateException(
                            "Delivery rider reservation is missing during expiry.");
            } else {
                releasePickupCapacity(order.getPickupType(), order.getPickupSlot().getId());
            }
            orderInventoryReservationService.releasePendingOrderHolds(
                    order.getOrderNumber(), "Checkout reservation expired.");
            order.setOrderStatus(OrderStatus.CANCELLED);
            orderRepository.saveAndFlush(order);
            log.info(
                    "Checkout reservation expired: orderId={}, orderNumber={}, fulfillmentType={},"
                            + " reservationExpiresAt={}",
                    order.getId(),
                    order.getOrderNumber(),
                    order.getFulfillmentType(),
                    expiresAt);
            return true;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CheckoutReservationExpiryProcessor.class,
                    "expireReservation(String)");
        }
    }

    /**
     * Releases pickup capacity.
     *
     * @param pickupType the pickup type
     * @param slotId the slot id
     */
    private void releasePickupCapacity(PickupType pickupType, Long slotId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CheckoutReservationExpiryProcessor.class,
                        "releasePickupCapacity(PickupType,Long)");
        try {
            switch (pickupType) {
                case NORMAL -> pickupSlotReservationService.releaseNormalCapacity(slotId);
                case PRIORITY -> pickupSlotReservationService.releasePriorityCapacity(slotId);
                case ADMIN_OVERRIDE ->
                        log.warn(
                                "Expired customer checkout unexpectedly uses ADMIN_OVERRIDE:"
                                        + " pickupSlotId={}",
                                slotId);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CheckoutReservationExpiryProcessor.class,
                    "releasePickupCapacity(PickupType,Long)");
        }
    }
}
