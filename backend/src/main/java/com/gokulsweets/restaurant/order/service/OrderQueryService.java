package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.CustomerOrderResponse;
import com.gokulsweets.restaurant.order.dto.CustomerOrderSummaryResponse;
import com.gokulsweets.restaurant.order.dto.OrderItemResponse;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderItem;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Coordinates order query operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderQueryService {

    private static final int MAX_HISTORY_ORDERS =
            AppConstant.ORDER_QUERY_SERVICE_MAX_HISTORY_ORDERS;

    private final OrderRepository orderRepository;

    private final PaymentRepository paymentRepository;

    private final JdbcTemplate jdbc;

    /**
     * Returns customer order.
     *
     * @param orderNumber the order number
     * @return the get customer order result
     */
    @Transactional(readOnly = true)
    public CustomerOrderResponse getCustomerOrder(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderQueryService.class, "getCustomerOrder(String)");
        try {
            log.debug("Retrieving customer order: orderNumber={}", orderNumber);
            Order order =
                    orderRepository
                            .findDetailedByOrderNumber(orderNumber)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Customer order not found: orderNumber={}",
                                                orderNumber);
                                        return new IllegalArgumentException(
                                                "Order does not exist.");
                                    });
            PaymentStatus paymentStatus =
                    paymentRepository
                            .findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                            .map(Payment::getPaymentStatus)
                            .orElse(null);
            List<OrderItemResponse> itemResponses =
                    order.getItems().stream().map(this::toItemResponse).toList();
            var window = deliveryWindow(order);
            var slot = order.getPickupSlot();
            return new CustomerOrderResponse(
                    order.getOrderNumber(),
                    order.getCustomerOrderNumber(),
                    order.getOrderStatus(),
                    paymentStatus,
                    order.getBranch().getName(),
                    order.getBranch().getAddress(),
                    slot == null ? null : slot.getSlotDate(),
                    slot == null ? null : slot.getStartTime(),
                    slot == null ? null : slot.getEndTime(),
                    order.getPickupType(),
                    order.getCustomerName(),
                    maskPhone(order.getCustomerPhone()),
                    itemResponses,
                    order.getSubtotal(),
                    order.getTaxAmount(),
                    order.getPriorityCharge(),
                    order.getConvenienceFee(),
                    order.getConvenienceFeeTax(),
                    order.getPaymentFee(),
                    order.getPaymentFeeTax(),
                    order.getPaymentFeeRate(),
                    order.getTotalAmount(),
                    order.getReservationExpiresAt(),
                    order.getCreatedAt(),
                    order.getUpdatedAt(),
                    order.getBranch().getPhone(),
                    order.getEstimatedReadyAt(),
                    order.getDelayReason(),
                    order.getDelayReportedAt(),
                    order.getFulfillmentType(),
                    window == null ? null : window.date(),
                    window == null ? null : window.start(),
                    window == null ? null : window.end(),
                    order.getDeliveryAddressLine(),
                    order.getDeliveryLocality(),
                    order.getDeliveryPostalCode(),
                    order.getBranch().getFssaiLicenceNumber(),
                    order.getDeliveryFee(),
                    order.getLoyaltyDiscount(),
                    order.getLoyaltyCoins(),
                    order.getLoyaltyRewardCode(),
                    order.getRebateDiscountAmount(),
                    order.getBranch().getId(),
                    slot == null ? null : slot.getId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderQueryService.class, "getCustomerOrder(String)");
        }
    }

    /**
     * Returns customer order history.
     *
     * @param suppliedOrderNumbers the supplied order numbers
     * @return the get customer order history result
     */
    @Transactional(readOnly = true)
    public List<CustomerOrderSummaryResponse> getCustomerOrderHistory(
            List<String> suppliedOrderNumbers) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderQueryService.class, "getCustomerOrderHistory(List<String>)");
        try {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String suppliedOrderNumber : suppliedOrderNumbers) {
                if (suppliedOrderNumber == null) {
                    continue;
                }
                String orderNumber = suppliedOrderNumber.trim().toUpperCase();
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
            List<Order> orders =
                    orderRepository.findByOrderNumberInOrderByCreatedAtDesc(
                            List.copyOf(normalized));
            log.debug(
                    "Customer order history retrieved: requestedCount={}, matchedCount={}",
                    normalized.size(),
                    orders.size());
            List<Long> deliveryWindowIds =
                    orders.stream()
                            .filter(order -> order.getFulfillmentType() == FulfillmentType.DELIVERY)
                            .map(Order::getDeliveryWindowId)
                            .distinct()
                            .toList();
            Map<Long, DeliveryWindow> windows =
                    deliveryWindowIds.isEmpty()
                            ? Map.of()
                            : jdbc.query(
                                    """
SELECT id, service_date, starts_at, ends_at FROM delivery_capacity_windows
WHERE id IN (%s)
"""
                                            .formatted(
                                                    String.join(
                                                            ",",
                                                            Collections.nCopies(
                                                                    deliveryWindowIds.size(),
                                                                    "?"))),
                                    rs -> {
                                        var result = new java.util.HashMap<Long, DeliveryWindow>();
                                        while (rs.next())
                                            result.put(
                                                    rs.getLong(1),
                                                    new DeliveryWindow(
                                                            rs.getDate(2).toLocalDate(),
                                                            rs.getTime(3).toLocalTime(),
                                                            rs.getTime(4).toLocalTime()));
                                        return result;
                                    },
                                    deliveryWindowIds.toArray());
            return orders.stream().map(order -> toSummaryResponse(order, windows)).toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderQueryService.class,
                    "getCustomerOrderHistory(List<String>)");
        }
    }

    /**
     * Tos summary response.
     *
     * @param order the order
     * @param windows the windows
     * @return the to summary response result
     */
    private CustomerOrderSummaryResponse toSummaryResponse(
            Order order, Map<Long, DeliveryWindow> windows) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OrderQueryService.class,
                        "toSummaryResponse(Order,Map<Long,DeliveryWindow>)");
        try {
            var window =
                    order.getFulfillmentType() == FulfillmentType.DELIVERY
                            ? windows.get(order.getDeliveryWindowId())
                            : null;
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY && window == null)
                throw new IllegalStateException("Delivery order is missing its rider window.");
            var slot = order.getPickupSlot();
            return new CustomerOrderSummaryResponse(
                    order.getOrderNumber(),
                    order.getCustomerOrderNumber(),
                    order.getOrderStatus(),
                    order.getBranch().getId(),
                    order.getBranch().getName(),
                    slot == null ? null : slot.getSlotDate(),
                    slot == null ? null : slot.getStartTime(),
                    slot == null ? null : slot.getEndTime(),
                    order.getPickupType(),
                    order.getTotalAmount(),
                    order.getCreatedAt(),
                    order.getUpdatedAt(),
                    order.getEstimatedReadyAt(),
                    order.getDelayReportedAt(),
                    order.getFulfillmentType(),
                    window == null ? null : window.date(),
                    window == null ? null : window.start(),
                    window == null ? null : window.end());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderQueryService.class,
                    "toSummaryResponse(Order,Map<Long,DeliveryWindow>)");
        }
    }

    /**
     * Delivery window.
     *
     * @param order the order
     * @return the delivery window result
     */
    private DeliveryWindow deliveryWindow(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderQueryService.class, "deliveryWindow(Order)");
        try {
            if (order.getFulfillmentType() != FulfillmentType.DELIVERY) return null;
            return jdbc
                    .query(
                            """
SELECT service_date, starts_at, ends_at FROM delivery_capacity_windows WHERE id = ?
""",
                            (rs, row) ->
                                    new DeliveryWindow(
                                            rs.getDate(1).toLocalDate(),
                                            rs.getTime(2).toLocalTime(),
                                            rs.getTime(3).toLocalTime()),
                            order.getDeliveryWindowId())
                    .stream()
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "Delivery order is missing its rider window."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderQueryService.class, "deliveryWindow(Order)");
        }
    }

    /** Immutable delivery window data contract. */
    private record DeliveryWindow(LocalDate date, LocalTime start, LocalTime end) {}

    /**
     * Tos item response.
     *
     * @param item the item
     * @return the to item response result
     */
    private OrderItemResponse toItemResponse(OrderItem item) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderQueryService.class, "toItemResponse(OrderItem)");
        try {
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
                    item.getLineTotal());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderQueryService.class,
                    "toItemResponse(OrderItem)");
        }
    }

    /**
     * Masks phone.
     *
     * @param phone the phone
     * @return the mask phone result
     */
    private String maskPhone(String phone) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderQueryService.class, "maskPhone(String)");
        try {
            if (phone == null || phone.isBlank()) {
                return null;
            }
            String trimmed = phone.trim();
            if (trimmed.length() <= 4) {
                return "****";
            }
            return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderQueryService.class, "maskPhone(String)");
        }
    }
}
