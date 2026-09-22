package com.gokulsweets.restaurant.order.lifecycle.scheduler;

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

@Component
@RequiredArgsConstructor
@Slf4j
public class PickupLifecycleScheduler {

    private final PickupLifecycleOrderRepository orderRepository;
    private final PickupLifecycleProcessor processor;
    private final PickupLifecycleProperties properties;
    private final Clock inventoryClock;

    @Scheduled(fixedDelayString = "${order.pickup-lifecycle.check-milliseconds:60000}")
    public void processPickupWindows() {
        if (!properties.isAutomaticExpiryEnabled()) return;

        LocalDateTime now = LocalDateTime.now(inventoryClock);
        process(
                dueOrders(
                        OrderStatus.READY_FOR_PICKUP,
                        now.minusMinutes(properties.getPickupExpiryGraceMinutes())
                ),
                true
        );
        process(
                dueOrders(
                        OrderStatus.PICKUP_WINDOW_EXPIRED,
                        now.minusMinutes(properties.getNoShowAfterMinutes())
                ),
                false
        );
    }

    private List<String> dueOrders(OrderStatus status, LocalDateTime cutoff) {
        return orderRepository.findDueOrderNumbers(
                status,
                cutoff.toLocalDate(),
                cutoff.toLocalTime(),
                PageRequest.of(0, properties.getBatchSize())
        );
    }

    private void process(List<String> orderNumbers, boolean expiryStage) {
        for (String orderNumber : orderNumbers) {
            try {
                if (expiryStage) processor.expirePickupWindow(orderNumber);
                else processor.markNoShow(orderNumber);
            } catch (RuntimeException exception) {
                log.error(
                        "Pickup lifecycle processing failed: orderNumber={}, stage={}",
                        orderNumber,
                        expiryStage ? "EXPIRY" : "NO_SHOW",
                        exception
                );
            }
        }
    }
}
