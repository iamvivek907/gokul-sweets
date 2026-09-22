package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.inventory.service.OrderInventoryReservationService;
import com.gokulsweets.restaurant.order.entity.Order;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutReservationExpiryProcessor {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Asia/Kolkata"
            );

    private final OrderRepository orderRepository;

    private final PaymentRepository paymentRepository;

    private final PickupSlotReservationService
            pickupSlotReservationService;

    private final OrderInventoryReservationService
            orderInventoryReservationService;

    /*
     * Processes one order in its own transaction.
     *
     * Pickup capacity, inventory holds and order status are
     * released together. A failure in any step rolls back all
     * three effects.
     */
    @Transactional
    public boolean expireReservation(
            String orderNumber
    ) {
        Order order = orderRepository
                .findForUpdate(orderNumber)
                .orElse(null);

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
                    order.getOrderNumber()
            );
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
                    "Skipping checkout reservation expiry because payment exists: orderId={}, orderNumber={}",
                    order.getId(),
                    order.getOrderNumber()
            );
            return false;
        }

        Long slotId = order.getPickupSlot().getId();

        releasePickupCapacity(
                order.getPickupType(),
                slotId
        );

        orderInventoryReservationService
                .releasePendingOrderHolds(
                        order.getOrderNumber(),
                        "Checkout reservation expired."
                );

        order.setOrderStatus(OrderStatus.CANCELLED);

        orderRepository.saveAndFlush(order);

        log.info(
                "Checkout reservation expired: orderId={}, orderNumber={}, pickupSlotId={}, pickupType={}, reservationExpiresAt={}",
                order.getId(),
                order.getOrderNumber(),
                slotId,
                order.getPickupType(),
                expiresAt
        );

        return true;
    }

    private void releasePickupCapacity(
            PickupType pickupType,
            Long slotId
    ) {
        switch (pickupType) {
            case NORMAL ->
                    pickupSlotReservationService
                            .releaseNormalCapacity(slotId);

            case PRIORITY ->
                    pickupSlotReservationService
                            .releasePriorityCapacity(slotId);

            case ADMIN_OVERRIDE ->
                    log.warn(
                            "Expired customer checkout unexpectedly uses ADMIN_OVERRIDE: pickupSlotId={}",
                            slotId
                    );
        }
    }
}
