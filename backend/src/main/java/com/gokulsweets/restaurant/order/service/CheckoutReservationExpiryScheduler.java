package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Backend checkout reservation expiry scheduler contract and implementation. */
@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutReservationExpiryScheduler {

    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;

    private final CheckoutReservationExpiryProcessor checkoutReservationExpiryProcessor;

    /**
     * Performs the expire pending checkout reservations operation for checkout reservation expiry
     * scheduler.
     */
    @Scheduled(fixedDelayString = "${checkout.reservation-expiry-check-interval-ms:60000}")
    public void expirePendingCheckoutReservations() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CheckoutReservationExpiryScheduler.class,
                        "expirePendingCheckoutReservations()");
        try {
            if (dedicatedImportWorker) return;
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            List<Order> candidates =
                    orderRepository
                            .findTop100ByOrderStatusAndReservationExpiresAtBeforeOrderByReservationExpiresAtAsc(
                                    OrderStatus.PENDING_PAYMENT, now);
            if (candidates.isEmpty()) {
                return;
            }
            log.debug("Found {} expired checkout reservation candidates.", candidates.size());
            int expiredCount = 0;
            for (Order candidate : candidates) {
                try {
                    boolean expired =
                            checkoutReservationExpiryProcessor.expireReservation(
                                    candidate.getOrderNumber());
                    if (expired) {
                        expiredCount++;
                    }
                } catch (Exception exception) {
                    /*
                     * One problematic order must not prevent other
                     * expired reservations from being released.
                     */
                    log.error(
                            "Unable to expire checkout reservation: orderNumber={}",
                            candidate.getOrderNumber(),
                            exception);
                }
            }
            if (expiredCount > 0) {
                log.info("Expired {} checkout reservations.", expiredCount);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CheckoutReservationExpiryScheduler.class,
                    "expirePendingCheckoutReservations()");
        }
    }
}
