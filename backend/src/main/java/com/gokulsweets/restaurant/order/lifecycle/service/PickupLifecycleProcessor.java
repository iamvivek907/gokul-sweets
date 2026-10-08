package com.gokulsweets.restaurant.order.lifecycle.service;

import com.gokulsweets.restaurant.inventory.service.OrderInventoryLifecycleService;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.lifecycle.config.PickupLifecycleProperties;
import com.gokulsweets.restaurant.order.lifecycle.repository.PickupLifecycleOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Backend pickup lifecycle processor contract and implementation. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PickupLifecycleProcessor {

    private final PickupLifecycleOrderRepository orderRepository;

    private final OrderInventoryLifecycleService inventoryLifecycleService;

    private final PickupLifecycleProperties properties;

    private final Clock inventoryClock;

    private final com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox
            notifications;

    /**
     * Expires pickup window.
     *
     * @param orderNumber the order number
     * @return the expire pickup window result
     */
    @Transactional
    public boolean expirePickupWindow(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupLifecycleProcessor.class, "expirePickupWindow(String)");
        try {
            Order order = orderRepository.findByOrderNumberForUpdate(orderNumber).orElse(null);
            if (order == null || order.getOrderStatus() != OrderStatus.READY_FOR_PICKUP) {
                return false;
            }
            LocalDateTime dueAt =
                    pickupEnd(order).plusMinutes(properties.getPickupExpiryGraceMinutes());
            if (dueAt.isAfter(LocalDateTime.now(inventoryClock))) {
                return false;
            }
            order.setOrderStatus(OrderStatus.PICKUP_WINDOW_EXPIRED);
            orderRepository.saveAndFlush(order);
            notifications.orderReady(order.getId());
            log.info("Pickup window expired: orderNumber={}, dueAt={}", orderNumber, dueAt);
            return true;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupLifecycleProcessor.class,
                    "expirePickupWindow(String)");
        }
    }

    /**
     * Marks no show.
     *
     * @param orderNumber the order number
     * @return the mark no show result
     */
    @Transactional
    public boolean markNoShow(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupLifecycleProcessor.class, "markNoShow(String)");
        try {
            Order order = orderRepository.findByOrderNumberForUpdate(orderNumber).orElse(null);
            if (order == null || order.getOrderStatus() != OrderStatus.PICKUP_WINDOW_EXPIRED) {
                return false;
            }
            LocalDateTime dueAt = pickupEnd(order).plusMinutes(properties.getNoShowAfterMinutes());
            if (dueAt.isAfter(LocalDateTime.now(inventoryClock))) {
                return false;
            }
            /*
             * The paid order becomes a no-show, not a refund. Its confirmed
             * allocation is released for reconciliation. Physical food is not
             * automatically called waste; staff records sale/carry/waste later.
             */
            inventoryLifecycleService.cancelOrderInventory(
                    orderNumber,
                    "Customer did not collect the order within the no-show window.",
                    "system");
            order.setOrderStatus(OrderStatus.NO_SHOW);
            orderRepository.saveAndFlush(order);
            notifications.orderReady(order.getId());
            log.info("Order marked no-show: orderNumber={}, dueAt={}", orderNumber, dueAt);
            return true;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupLifecycleProcessor.class,
                    "markNoShow(String)");
        }
    }

    /**
     * Pickups end.
     *
     * @param order the order
     * @return the pickup end result
     */
    private LocalDateTime pickupEnd(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupLifecycleProcessor.class, "pickupEnd(Order)");
        try {
            return LocalDateTime.of(
                    order.getPickupSlot().getSlotDate(), order.getPickupSlot().getEndTime());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PickupLifecycleProcessor.class, "pickupEnd(Order)");
        }
    }
}
