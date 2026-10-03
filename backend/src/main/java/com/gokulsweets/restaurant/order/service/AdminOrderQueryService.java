package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.delivery.DeliveryPreparationQueue;
import com.gokulsweets.restaurant.order.config.PreparationWindowProperties;
import com.gokulsweets.restaurant.order.dto.OrderItemResponse;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderDetailResponse;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderPageResponse;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderQueueCountsResponse;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderQueueItemResponse;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderQueueResponse;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderSummaryResponse;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderItem;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOrderQueryService {

    private static final int MAX_PAGE_SIZE =
            100;


    private static final int DEFAULT_QUEUE_LIMIT =
            50;


    private static final int MAX_QUEUE_LIMIT =
            200;


    private final OrderRepository
            orderRepository;


    private final PaymentRepository
            paymentRepository;


    private final StaffAuthorizationService
            staffAuthorizationService;


    private final PreparationEligibilityService
            preparationEligibilityService;


    private final PreparationWindowProperties
            preparationWindowProperties;

    private final ApplicationClock  applicationClock;
    private final JdbcTemplate jdbc;
    private final DeliveryPreparationQueue deliveryPreparationQueue;

    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrderByCustomerNumber(long number) {
        if (number < 1) throw new IllegalArgumentException("Enter a valid order number.");
        Order order = orderRepository.findByCustomerOrderNumber(number)
                .orElseThrow(() -> new IllegalArgumentException("Order does not exist."));
        // Retains the existing branch authorization and detailed response mapping.
        return getOrder(order.getOrderNumber());
    }


    /*
     * =========================================================
     * GENERAL ORDER LIST
     * =========================================================
     */

    @PreAuthorize(
            "hasAuthority('ORDER_VIEW')"
    )
    @Transactional(readOnly = true)
    public AdminOrderPageResponse getOrders(
            Long branchId,
            OrderStatus status,
            int page,
            int size
    ) {

        validateBranchId(
                branchId
        );


        staffAuthorizationService
                .requireBranchAccess(
                        branchId
                );


        if (page < 0) {

            throw new IllegalArgumentException(
                    "Page must not be negative."
            );
        }


        int safeSize =
                Math.clamp(
                        size,
                        1
                        ,
                        MAX_PAGE_SIZE
                );


        PageRequest pageable =
                PageRequest.of(
                        page,
                        safeSize,
                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );


        Page<Order> orders =
                status == null
                        ? orderRepository
                        .findByBranchId(
                                branchId,
                                pageable
                        )
                        : orderRepository
                        .findByBranchIdAndOrderStatus(
                                branchId,
                                status,
                                pageable
                        );


        Map<Long, PaymentStatus> paymentStatuses =
                loadLatestPaymentStatuses(
                        orders.getContent()
                );

        Map<Long, DeliveryWindow> windows = loadDeliveryWindows(orders.getContent());


        List<AdminOrderSummaryResponse> responses =
                orders.getContent()
                        .stream()
                        .map(
                                order ->
                                        toSummaryResponse(
                                                order,
                                                paymentStatuses.get(order.getId()),
                                                order.getFulfillmentType() == FulfillmentType.DELIVERY
                                                        ? windows.get(order.getDeliveryWindowId()) : null
                                        )
                        )
                        .toList();


        log.debug(
                "Admin orders retrieved: branchId={}, status={}, page={}, size={}, resultCount={}",
                branchId,
                status,
                page,
                safeSize,
                responses.size()
        );


        return new AdminOrderPageResponse(
                responses,
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages()
        );
    }


    /*
     * =========================================================
     * OPERATIONAL PREPARATION QUEUE
     * =========================================================
     */

    @PreAuthorize(
            "hasAuthority('ORDER_VIEW')"
    )
    @Transactional(readOnly = true)
    public AdminOrderQueueResponse getPreparationQueue(
            Long branchId,
            Integer limit
    ) {

        validateBranchId(
                branchId
        );


        staffAuthorizationService
                .requireBranchAccess(
                        branchId
                );


        int safeLimit =
                resolveQueueLimit(
                        limit
                );


        int databaseLimit =
                safeLimit
                        +
                        1;


        LocalDateTime now = applicationClock.now();


        QueueCutoffs cutoffs =
                createQueueCutoffs(
                        now
                );


        PageRequest queuePage =
                PageRequest.of(
                        0,
                        databaseLimit
                );


        List<Order> pickupCandidates =
                orderRepository
                        .findPreparationQueueCandidates(
                                branchId,
                                OrderStatus.CONFIRMED,

                                PickupType.NORMAL,
                                cutoffs.normal()
                                        .toLocalDate(),
                                cutoffs.normal()
                                        .toLocalTime(),

                                PickupType.PRIORITY,
                                cutoffs.priority()
                                        .toLocalDate(),
                                cutoffs.priority()
                                        .toLocalTime(),

                                PickupType.ADMIN_OVERRIDE,
                                cutoffs.adminOverride()
                                        .toLocalDate(),
                                cutoffs.adminOverride()
                                        .toLocalTime(),

                                queuePage
                        );

        List<Order> deliveryCandidates = deliveryPreparationQueue.eligible(branchId, now, databaseLimit);
        List<Order> candidates = new java.util.ArrayList<>(pickupCandidates);
        candidates.addAll(deliveryCandidates);
        Map<Long, DeliveryWindow> windows = loadDeliveryWindows(deliveryCandidates);
        candidates.sort(Comparator.comparing((Order order) ->
                        order.getFulfillmentType() == FulfillmentType.DELIVERY
                                ? LocalDateTime.of(windows.get(order.getDeliveryWindowId()).date(),
                                        windows.get(order.getDeliveryWindowId()).start())
                                : LocalDateTime.of(order.getPickupSlot().getSlotDate(),
                                        order.getPickupSlot().getStartTime()))
                .thenComparing(Order::getCreatedAt).thenComparing(Order::getId));


        boolean hasMore =
                candidates.size()
                        >
                        safeLimit;


        List<Order> queueOrders =
                hasMore
                        ? candidates.subList(
                        0,
                        safeLimit
                )
                        : candidates;


        Map<Long, PaymentStatus> paymentStatuses =
                loadLatestPaymentStatuses(
                        queueOrders
                );


        List<AdminOrderQueueItemResponse> responses =
                queueOrders
                        .stream()
                        .map(
                                order ->
                                        toQueueResponse(
                                                order,
                                                paymentStatuses.get(
                                                        order.getId()
                                                ),
                                                now,
                                                order.getFulfillmentType() == FulfillmentType.DELIVERY
                                                        ? windows.get(order.getDeliveryWindowId()) : null
                                        )
                        )
                        .toList();


        log.debug(
                "Preparation queue retrieved: branchId={}, limit={}, resultCount={}, hasMore={}, generatedAt={}",
                branchId,
                safeLimit,
                responses.size(),
                hasMore,
                now
        );


        return new AdminOrderQueueResponse(
                responses,
                responses.size(),
                safeLimit,
                hasMore,
                now
        );
    }


    /*
     * =========================================================
     * OPERATIONAL QUEUE COUNTS
     * =========================================================
     */

    @PreAuthorize(
            "hasAuthority('ORDER_VIEW')"
    )
    @Transactional(readOnly = true)
    public AdminOrderQueueCountsResponse
    getPreparationQueueCounts(
            Long branchId
    ) {

        validateBranchId(
                branchId
        );


        staffAuthorizationService
                .requireBranchAccess(
                        branchId
                );


        LocalDateTime now = applicationClock.now();


        QueueCutoffs cutoffs =
                createQueueCutoffs(
                        now
                );


        long confirmedTotal =
                orderRepository
                        .countByBranchIdAndOrderStatusAndFulfillmentType(
                                branchId,
                                OrderStatus.CONFIRMED,
                                FulfillmentType.PICKUP
                        ) + deliveryPreparationQueue.confirmed(branchId);


        long actionableTotal =
                orderRepository
                        .countPreparationQueueCandidates(
                                branchId,
                                OrderStatus.CONFIRMED,

                                PickupType.NORMAL,
                                cutoffs.normal()
                                        .toLocalDate(),
                                cutoffs.normal()
                                        .toLocalTime(),

                                PickupType.PRIORITY,
                                cutoffs.priority()
                                        .toLocalDate(),
                                cutoffs.priority()
                                        .toLocalTime(),

                                PickupType.ADMIN_OVERRIDE,
                                cutoffs.adminOverride()
                                        .toLocalDate(),
                                cutoffs.adminOverride()
                                        .toLocalTime()
                        ) + deliveryPreparationQueue.eligibleCount(branchId, now);


        long overdue =
                orderRepository
                        .countOverdueConfirmedOrders(
                                branchId,
                                OrderStatus.CONFIRMED,
                                now.toLocalDate(),
                                now.toLocalTime()
                        ) + deliveryPreparationQueue.overdueCount(branchId, now);


        /*
         * Every overdue CONFIRMED order is necessarily also
         * inside its preparation window.
         *
         * Therefore:
         *
         * actionable = overdue + eligible
         */
        long eligible =
                Math.max(
                        actionableTotal
                                -
                                overdue,
                        0
                );


        /*
         * CONFIRMED orders whose preparation window has not
         * opened yet.
         */
        long scheduled =
                Math.max(
                        confirmedTotal
                                -
                                actionableTotal,
                        0
                );


        long preparing =
                orderRepository
                        .countByBranchIdAndOrderStatusAndFulfillmentType(
                                branchId,
                                OrderStatus.PREPARING,
                                FulfillmentType.PICKUP
                        ) + orderRepository.countByBranchIdAndOrderStatusAndFulfillmentType(
                                branchId, OrderStatus.PREPARING, FulfillmentType.DELIVERY);


        long ready =
                orderRepository
                        .countByBranchIdAndOrderStatusAndFulfillmentType(
                                branchId,
                                OrderStatus.READY_FOR_PICKUP,
                                FulfillmentType.PICKUP
                        );


        log.debug(
                "Preparation queue counts retrieved: branchId={}, overdue={}, eligible={}, scheduled={}, preparing={}, ready={}, confirmedTotal={}, actionableTotal={}",
                branchId,
                overdue,
                eligible,
                scheduled,
                preparing,
                ready,
                confirmedTotal,
                actionableTotal
        );


        return new AdminOrderQueueCountsResponse(
                overdue,
                eligible,
                scheduled,
                preparing,
                ready,
                confirmedTotal,
                actionableTotal,
                now
        );
    }


    /*
     * =========================================================
     * ORDER DETAIL
     * =========================================================
     */

    @PreAuthorize(
            "hasAuthority('ORDER_VIEW')"
    )
    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrder(
            String orderNumber
    ) {

        Order order =
                orderRepository
                        .findDetailedByOrderNumber(
                                orderNumber
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Order does not exist."
                                        )
                        );


        staffAuthorizationService
                .requireBranchAccess(
                        order.getBranch()
                                .getId()
                );


        PaymentStatus paymentStatus =
                paymentRepository
                        .findFirstByOrderIdOrderByCreatedAtDesc(
                                order.getId()
                        )
                        .map(
                                Payment::getPaymentStatus
                        )
                        .orElse(null);


        List<OrderItemResponse> items =
                order.getItems()
                        .stream()
                        .map(
                                this::toItemResponse
                        )
                        .toList();

        DeliveryWindow window = order.getFulfillmentType() == FulfillmentType.DELIVERY
                ? loadDeliveryWindows(List.of(order)).get(order.getDeliveryWindowId()) : null;
        var slot = order.getPickupSlot();


        return new AdminOrderDetailResponse(
                order.getOrderNumber(),
                order.getCustomerOrderNumber(),

                order.getBranch()
                        .getId(),

                order.getBranch()
                        .getName(),

                order.getBranch()
                        .getAddress(),

                order.getCustomerName(),

                order.getCustomerPhone(),

                slot == null ? null : slot.getSlotDate(),

                slot == null ? null : slot.getStartTime(),

                slot == null ? null : slot.getEndTime(),

                order.getPickupType(),

                order.getOrderStatus(),

                paymentStatus,

                order.getSubtotal(),

                order.getTaxAmount(),

                order.getPriorityCharge(),
                order.getConvenienceFee(),
                order.getConvenienceFeeTax(),

                order.getTotalAmount(),

                order.isAdminOverride(),

                order.getOverrideReason(),

                items,

                order.getCreatedAt(),

                order.getUpdatedAt(),
                order.getEstimatedReadyAt(),
                order.getDelayReason(),
                order.getDelayReportedAt(),
                order.getFulfillmentType(),
                window == null ? null : window.date(),
                window == null ? null : window.start(),
                window == null ? null : window.end(),
                order.getDeliveryAddressLine(),
                order.getDeliveryLocality(),
                order.getDeliveryPostalCode()
        );
    }


    /*
     * =========================================================
     * QUEUE CUTOFFS
     * =========================================================
     */

    private QueueCutoffs createQueueCutoffs(
            LocalDateTime now
    ) {

        LocalDateTime normal =
                now.plusMinutes(
                        validatedLeadMinutes(
                                preparationWindowProperties
                                        .getNormalLeadMinutes(),
                                "Normal"
                        )
                );


        LocalDateTime priority =
                now.plusMinutes(
                        validatedLeadMinutes(
                                preparationWindowProperties
                                        .getPriorityLeadMinutes(),
                                "Priority"
                        )
                );


        LocalDateTime adminOverride =
                now.plusMinutes(
                        validatedLeadMinutes(
                                preparationWindowProperties
                                        .getAdminOverrideLeadMinutes(),
                                "Admin override"
                        )
                );


        return new QueueCutoffs(
                normal,
                priority,
                adminOverride
        );
    }


    /*
     * =========================================================
     * PAYMENT STATUS LOOKUP
     * =========================================================
     */

    private Map<Long, PaymentStatus> loadLatestPaymentStatuses(
            List<Order> orders
    ) {

        if (orders.isEmpty()) {

            return Collections.emptyMap();
        }


        List<Long> orderIds =
                orders.stream()
                        .map(
                                Order::getId
                        )
                        .toList();


        return paymentRepository
                .findLatestPaymentsForOrders(
                        orderIds
                )
                .stream()
                .collect(
                        Collectors.toMap(
                                payment ->
                                        payment.getOrder()
                                                .getId(),

                                Payment::getPaymentStatus,

                                (
                                        first,
                                        second
                                ) ->
                                        first
                        )
                );
    }


    /*
     * =========================================================
     * GENERAL SUMMARY MAPPING
     * =========================================================
     */

    private AdminOrderSummaryResponse toSummaryResponse(
            Order order,
            PaymentStatus paymentStatus,
            DeliveryWindow window
    ) {

        var slot = order.getPickupSlot();
        if (order.getFulfillmentType() == FulfillmentType.DELIVERY && window == null)
            throw new IllegalStateException("Delivery order is missing its rider window.");

        return new AdminOrderSummaryResponse(
                order.getOrderNumber(),
                order.getCustomerOrderNumber(),

                order.getBranch()
                        .getId(),

                order.getBranch()
                        .getName(),

                order.getCustomerName(),

                maskPhone(
                        order.getCustomerPhone()
                ),

                slot == null ? null : slot.getSlotDate(),

                slot == null ? null : slot.getStartTime(),

                slot == null ? null : slot.getEndTime(),

                order.getPickupType(),

                order.getTotalAmount(),

                order.getOrderStatus(),

                paymentStatus,

                order.getCreatedAt(),
                order.getEstimatedReadyAt(),
                order.getDelayReportedAt(),
                order.getFulfillmentType(),
                window == null ? null : window.date(),
                window == null ? null : window.start(),
                window == null ? null : window.end()
        );
    }

    private Map<Long, DeliveryWindow> loadDeliveryWindows(List<Order> orders) {
        List<Long> ids = orders.stream().filter(o -> o.getFulfillmentType() == FulfillmentType.DELIVERY)
                .map(Order::getDeliveryWindowId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, DeliveryWindow> windows = jdbc.query("""
                SELECT id, service_date, starts_at, ends_at FROM delivery_capacity_windows WHERE id IN (%s)
                """.formatted(String.join(",", Collections.nCopies(ids.size(), "?"))), rs -> {
                    var result = new java.util.HashMap<Long, DeliveryWindow>();
                    while (rs.next()) result.put(rs.getLong(1), new DeliveryWindow(rs.getDate(2).toLocalDate(),
                            rs.getTime(3).toLocalTime(), rs.getTime(4).toLocalTime()));
                    return result;
                }, ids.toArray());
        if (windows.size() != ids.size()) throw new IllegalStateException("Delivery order is missing its rider window.");
        return windows;
    }

    private record DeliveryWindow(LocalDate date, LocalTime start, LocalTime end) {}


    /*
     * =========================================================
     * QUEUE MAPPING
     * =========================================================
     */

    private AdminOrderQueueItemResponse toQueueResponse(
            Order order,
            PaymentStatus paymentStatus,
            LocalDateTime now,
            DeliveryWindow deliveryWindow
    ) {

        PreparationEligibility eligibility =
                preparationEligibilityService
                        .evaluate(
                                order,
                                now
                        );


        if (
                !eligibility
                        .canStartPreparation()
        ) {

            throw new IllegalStateException(
                    "Preparation queue returned an order that is not eligible for preparation."
            );
        }


        return new AdminOrderQueueItemResponse(
                order.getOrderNumber(),
                order.getCustomerOrderNumber(),

                order.getBranch()
                        .getId(),

                order.getBranch()
                        .getName(),

                order.getCustomerName(),

                maskPhone(
                        order.getCustomerPhone()
                ),

                deliveryWindow == null ? order.getPickupSlot().getSlotDate() : null,

                deliveryWindow == null ? order.getPickupSlot().getStartTime() : null,

                deliveryWindow == null ? order.getPickupSlot().getEndTime() : null,

                order.getPickupType(),

                order.getTotalAmount(),

                order.getOrderStatus(),

                paymentStatus,

                order.getCreatedAt(),

                eligibility.status(),

                eligibility.eligibleAt(),

                eligibility.pickupAt(),

                eligibility.minutesUntilPickup(),
                order.getFulfillmentType(),
                deliveryWindow == null ? null : deliveryWindow.date(),
                deliveryWindow == null ? null : deliveryWindow.start(),
                deliveryWindow == null ? null : deliveryWindow.end()
        );
    }


    /*
     * =========================================================
     * ITEM MAPPING
     * =========================================================
     */

    private OrderItemResponse toItemResponse(
            OrderItem item
    ) {

        return new OrderItemResponse(
                item.getId(),

                item.getProduct()
                        .getId(),

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


    /*
     * =========================================================
     * VALIDATION
     * =========================================================
     */

    private void validateBranchId(
            Long branchId
    ) {

        if (branchId == null) {

            throw new IllegalArgumentException(
                    "Branch ID is required."
            );
        }
    }


    private int resolveQueueLimit(
            Integer limit
    ) {

        if (limit == null) {

            return DEFAULT_QUEUE_LIMIT;
        }


        return Math.clamp(
                limit,
                1
                ,
                MAX_QUEUE_LIMIT
        );
    }


    private int validatedLeadMinutes(
            int leadMinutes,
            String label
    ) {

        if (leadMinutes < 0) {

            throw new IllegalStateException(
                    label
                            +
                            " preparation lead minutes cannot be negative."
            );
        }


        return leadMinutes;
    }


    /*
     * =========================================================
     * PHONE MASKING
     * =========================================================
     */

    private String maskPhone(
            String phone
    ) {

        if (
                phone == null
                        ||
                        phone.isBlank()
        ) {

            return null;
        }


        String trimmed =
                phone.trim();


        if (
                trimmed.length()
                        <=
                        4
        ) {

            return "****";
        }


        return "*"
                .repeat(
                        trimmed.length()
                                -
                                4
                )
                +
                trimmed.substring(
                        trimmed.length()
                                -
                                4
                );
    }


    /*
     * =========================================================
     * INTERNAL QUEUE CUTOFF VALUE
     * =========================================================
     */

    private record QueueCutoffs(

            LocalDateTime normal,

            LocalDateTime priority,

            LocalDateTime adminOverride

    ) {
    }
}
