package com.gokulsweets.restaurant.order.lifecycle.service;

import com.gokulsweets.restaurant.inventory.service.OrderInventoryLifecycleService;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class PickupLifecycleProcessor {

    private final PickupLifecycleOrderRepository orderRepository;
    private final OrderInventoryLifecycleService inventoryLifecycleService;
    private final PickupLifecycleProperties properties;
    private final Clock inventoryClock;

    @Transactional
    public boolean expirePickupWindow(String orderNumber) {
        Order order = orderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElse(null);
        if (order == null || order.getOrderStatus() != OrderStatus.READY_FOR_PICKUP) {
            return false;
        }

        LocalDateTime dueAt = pickupEnd(order)
                .plusMinutes(properties.getPickupExpiryGraceMinutes());
        if (dueAt.isAfter(LocalDateTime.now(inventoryClock))) {
            return false;
        }

        order.setOrderStatus(OrderStatus.PICKUP_WINDOW_EXPIRED);
        orderRepository.saveAndFlush(order);

        log.info("Pickup window expired: orderNumber={}, dueAt={}", orderNumber, dueAt);
        return true;
    }

    @Transactional
    public boolean markNoShow(String orderNumber) {
        Order order = orderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElse(null);
        if (order == null || order.getOrderStatus() != OrderStatus.PICKUP_WINDOW_EXPIRED) {
            return false;
        }

        LocalDateTime dueAt = pickupEnd(order)
                .plusMinutes(properties.getNoShowAfterMinutes());
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
                "system"
        );
        order.setOrderStatus(OrderStatus.NO_SHOW);
        orderRepository.saveAndFlush(order);

        log.info("Order marked no-show: orderNumber={}, dueAt={}", orderNumber, dueAt);
        return true;
    }

    private LocalDateTime pickupEnd(Order order) {
        return LocalDateTime.of(
                order.getPickupSlot().getSlotDate(),
                order.getPickupSlot().getEndTime()
        );
    }
}
