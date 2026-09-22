package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.order.dto.CustomerOrderResponse;
import com.gokulsweets.restaurant.order.dto.CustomerOrderSummaryResponse;
import com.gokulsweets.restaurant.order.dto.OrderItemResponse;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderItem;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderQueryService {

    private static final int MAX_HISTORY_ORDERS = 500;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public CustomerOrderResponse getCustomerOrder(
            String orderNumber
    ) {
        log.debug(
                "Retrieving customer order: orderNumber={}",
                orderNumber
        );

        Order order = orderRepository
                .findDetailedByOrderNumber(orderNumber)
                .orElseThrow(() -> {
                    log.warn(
                            "Customer order not found: orderNumber={}",
                            orderNumber
                    );
                    return new IllegalArgumentException(
                            "Order does not exist."
                    );
                });

        PaymentStatus paymentStatus = paymentRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .map(Payment::getPaymentStatus)
                .orElse(null);

        List<OrderItemResponse> itemResponses = order.getItems()
                .stream()
                .map(this::toItemResponse)
                .toList();

        return new CustomerOrderResponse(
                order.getOrderNumber(),
                order.getOrderStatus(),
                paymentStatus,
                order.getBranch().getName(),
                order.getBranch().getAddress(),
                order.getPickupSlot().getSlotDate(),
                order.getPickupSlot().getStartTime(),
                order.getPickupSlot().getEndTime(),
                order.getPickupType(),
                order.getCustomerName(),
                maskPhone(order.getCustomerPhone()),
                itemResponses,
                order.getSubtotal(),
                order.getTaxAmount(),
                order.getPriorityCharge(),
                order.getTotalAmount(),
                order.getReservationExpiresAt(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<CustomerOrderSummaryResponse> getCustomerOrderHistory(
            List<String> suppliedOrderNumbers
    ) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String suppliedOrderNumber : suppliedOrderNumbers) {
            if (suppliedOrderNumber == null) {
                continue;
            }

            String orderNumber = suppliedOrderNumber
                    .trim()
                    .toUpperCase();

            if (!orderNumber.isBlank()) {
                normalized.add(orderNumber);
            }

            if (normalized.size() >= MAX_HISTORY_ORDERS) {
                break;
            }
        }

        if (normalized.isEmpty()) {
            return List.of();
        }

        List<Order> orders = orderRepository
                .findByOrderNumberInOrderByCreatedAtDesc(
                        List.copyOf(normalized)
                );

        log.debug(
                "Customer order history retrieved: requestedCount={}, matchedCount={}",
                normalized.size(),
                orders.size()
        );

        return orders.stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    private CustomerOrderSummaryResponse toSummaryResponse(Order order) {
        return new CustomerOrderSummaryResponse(
                order.getOrderNumber(),
                order.getOrderStatus(),
                order.getBranch().getName(),
                order.getPickupSlot().getSlotDate(),
                order.getPickupSlot().getStartTime(),
                order.getPickupSlot().getEndTime(),
                order.getPickupType(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductName(),
                item.getSaleMode(),
                item.getQuantity(),
                item.getWeightGrams(),
                item.getUnitPrice(),
                item.getTaxRate(),
                item.getTaxAmount(),
                item.getLineTotal()
        );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String trimmed = phone.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }

        return "*".repeat(trimmed.length() - 4)
                + trimmed.substring(trimmed.length() - 4);
    }
}
