package com.gokulsweets.restaurant.order.lifecycle.scheduler;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.lifecycle.config.PickupLifecycleProperties;
import com.gokulsweets.restaurant.order.lifecycle.repository.PickupLifecycleOrderRepository;
import com.gokulsweets.restaurant.order.lifecycle.service.PickupLifecycleProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Backend pickup lifecycle scheduler contract and implementation. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PickupLifecycleScheduler {

    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private final PickupLifecycleOrderRepository orderRepository;

    private final PickupLifecycleProcessor processor;

    private final PickupLifecycleProperties properties;

    private final Clock inventoryClock;

    /** Processes pickup windows. */
    @Scheduled(fixedDelayString = "${order.pickup-lifecycle.check-milliseconds:60000}")
    public void processPickupWindows() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupLifecycleScheduler.class, "processPickupWindows()");
        try {
            if (dedicatedImportWorker) return;
            if (!properties.isAutomaticExpiryEnabled()) return;
            LocalDateTime now = LocalDateTime.now(inventoryClock);
            process(
                    dueOrders(
                            OrderStatus.READY_FOR_PICKUP,
                            now.minusMinutes(properties.getPickupExpiryGraceMinutes())),
                    true);
            process(
                    dueOrders(
                            OrderStatus.PICKUP_WINDOW_EXPIRED,
                            now.minusMinutes(properties.getNoShowAfterMinutes())),
                    false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupLifecycleScheduler.class,
                    "processPickupWindows()");
        }
    }

    /**
     * Dues orders.
     *
     * @param status the status
     * @param cutoff the cutoff
     * @return the due orders result
     */
    private List<String> dueOrders(OrderStatus status, LocalDateTime cutoff) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PickupLifecycleScheduler.class, "dueOrders(OrderStatus,LocalDateTime)");
        try {
            return orderRepository.findDueOrderNumbers(
                    status,
                    cutoff.toLocalDate(),
                    cutoff.toLocalTime(),
                    PageRequest.of(0, properties.getBatchSize()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupLifecycleScheduler.class,
                    "dueOrders(OrderStatus,LocalDateTime)");
        }
    }

    /**
     * Processes the operation.
     *
     * @param orderNumbers the order numbers
     * @param expiryStage the expiry stage
     */
    private void process(List<String> orderNumbers, boolean expiryStage) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupLifecycleScheduler.class, "process(List<String>,boolean)");
        try {
            for (String orderNumber : orderNumbers) {
                try {
                    if (expiryStage) processor.expirePickupWindow(orderNumber);
                    else processor.markNoShow(orderNumber);
                } catch (RuntimeException exception) {
                    log.error(
                            "Pickup lifecycle processing failed: orderNumber={}, stage={}",
                            orderNumber,
                            expiryStage ? "EXPIRY" : "NO_SHOW",
                            exception);
                }
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupLifecycleScheduler.class,
                    "process(List<String>,boolean)");
        }
    }
}
