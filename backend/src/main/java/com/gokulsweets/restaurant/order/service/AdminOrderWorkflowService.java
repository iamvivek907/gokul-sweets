package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.kot.entity.Kot;
import com.gokulsweets.restaurant.kot.service.KotService;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderDetailResponse;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.enums.PreparationBatchResult;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Coordinates admin order workflow operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOrderWorkflowService {

    private static final Map<OrderStatus, OrderStatus> ALLOWED_TRANSITIONS =
            Map.of(
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING,
                    OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_PICKUP,
                    OrderStatus.READY_FOR_PICKUP,
                    OrderStatus.PICKED_UP);

    private static final Map<OrderStatus, OrderStatus> DELIVERY_TRANSITIONS =
            Map.of(
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING,
                    OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_DELIVERY,
                    OrderStatus.READY_FOR_DELIVERY,
                    OrderStatus.OUT_FOR_DELIVERY,
                    OrderStatus.OUT_FOR_DELIVERY,
                    OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;

    private final AdminOrderQueryService adminOrderQueryService;

    private final StaffAuthorizationService staffAuthorizationService;

    private final KotService kotService;

    private final PreparationEligibilityService preparationEligibilityService;

    private final com.gokulsweets.restaurant.delivery.DeliveryDispatchPilotService dispatch;

    private final com.gokulsweets.restaurant.occasion.OccasionProductionReadinessService
            bulkReadiness;

    private final com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox
            notifications;

    private final PickupCodeService pickupCodes;

    private final com.gokulsweets.restaurant.loyalty.LoyaltyService loyalty;

    /*
     * =========================================================
     * STANDARD SINGLE-ORDER STATUS TRANSITION
     * =========================================================
     */
    /**
     * Transitions status.
     *
     * @param orderNumber the order number
     * @param targetStatus the target status
     * @return the transition status result
     */
    @Transactional(noRollbackFor = PickupCodeRejectedException.class)
    public AdminOrderDetailResponse transitionStatus(String orderNumber, OrderStatus targetStatus) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderWorkflowService.class, "transitionStatus(String,OrderStatus)");
        try {
            return transitionStatus(orderNumber, targetStatus, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderWorkflowService.class,
                    "transitionStatus(String,OrderStatus)");
        }
    }

    /**
     * Transitions status.
     *
     * @param orderNumber the order number
     * @param targetStatus the target status
     * @param pickupCode the pickup code
     * @return the transition status result
     */
    @Transactional(noRollbackFor = PickupCodeRejectedException.class)
    public AdminOrderDetailResponse transitionStatus(
            String orderNumber, OrderStatus targetStatus, String pickupCode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderWorkflowService.class,
                        "transitionStatus(String,OrderStatus,String)");
        try {
            Order order =
                    orderRepository
                            .findForUpdate(orderNumber)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Admin status transition failed because order does"
                                                        + " not exist: orderNumber={}",
                                                orderNumber);
                                        return new IllegalArgumentException(
                                                "Order does not exist.");
                                    });
            staffAuthorizationService.requireBranchAccess(order.getBranch().getId());
            OrderStatus currentStatus = order.getOrderStatus();
            /*
             * =====================================================
             * IDEMPOTENT DUPLICATE REQUEST
             * =====================================================
             */
            if (currentStatus == targetStatus) {
                if (targetStatus == OrderStatus.PICKED_UP)
                    staffAuthorizationService.requirePermission(
                            PermissionName.ORDER_MARK_PICKED_UP);
                log.debug(
                        "Ignoring duplicate admin order transition: orderNumber={}, status={}",
                        orderNumber,
                        currentStatus);
                return adminOrderQueryService.getOrder(orderNumber);
            }
            /*
             * =====================================================
             * BUSINESS WORKFLOW VALIDATION
             * =====================================================
             */
            OrderStatus allowedTarget =
                    (order.getFulfillmentType() == FulfillmentType.DELIVERY
                                    ? DELIVERY_TRANSITIONS
                                    : ALLOWED_TRANSITIONS)
                            .get(currentStatus);
            if (allowedTarget == null || allowedTarget != targetStatus) {
                log.warn(
                        "Invalid admin order transition: orderNumber={}, currentStatus={},"
                                + " requestedStatus={}",
                        orderNumber,
                        currentStatus,
                        targetStatus);
                throw new IllegalStateException(
                        "Order cannot move from " + currentStatus + " to " + targetStatus + ".");
            }
            /*
             * =====================================================
             * TRANSITION-SPECIFIC PERMISSION
             * =====================================================
             */
            requirePermissionForTransition(order.getFulfillmentType(), currentStatus, targetStatus);
            if (targetStatus == OrderStatus.PICKED_UP)
                pickupCodes.verifyAndConsume(order, pickupCode);
            if (targetStatus == OrderStatus.READY_FOR_PICKUP)
                bulkReadiness.requireReady(order.getId());
            if (order.getFulfillmentType() == FulfillmentType.DELIVERY
                    && targetStatus == OrderStatus.OUT_FOR_DELIVERY)
                dispatch.requireAssignment(order.getId());
            /*
             * =====================================================
             * PREPARATION ELIGIBILITY
             * =====================================================
             *
             * This closes the manual/detail-screen bypass.
             *
             * A CONFIRMED future order may not be moved to
             * PREPARING until its preparation window opens.
             */
            if (isStartPreparationTransition(currentStatus, targetStatus)) {
                requirePreparationEligibility(order);
            }
            /*
             * =====================================================
             * ATOMIC ORDER STATUS TRANSITION
             * =====================================================
             */
            int updatedRows =
                    orderRepository.transitionStatus(order.getId(), currentStatus, targetStatus);
            /*
             * =====================================================
             * CONCURRENT UPDATE
             * =====================================================
             */
            if (updatedRows == 1
                    && (targetStatus == OrderStatus.PICKED_UP
                            || targetStatus == OrderStatus.DELIVERED))
                loyalty.reconcile(order.getId());
            if (updatedRows == 1) notifications.orderReady(order.getId());
            if (updatedRows == 1 && order.getFulfillmentType() == FulfillmentType.DELIVERY) {
                if (targetStatus == OrderStatus.OUT_FOR_DELIVERY)
                    dispatch.recordTransition(order.getId(), "DISPATCHED");
                if (targetStatus == OrderStatus.DELIVERED)
                    dispatch.recordTransition(order.getId(), "DELIVERED");
            }
            if (updatedRows == 0) {
                Order latest = orderRepository.findById(order.getId()).orElseThrow();
                if (latest.getOrderStatus() == targetStatus) {
                    log.debug(
                            "Order transition already completed by another request: orderNumber={},"
                                    + " status={}",
                            orderNumber,
                            targetStatus);
                    return adminOrderQueryService.getOrder(orderNumber);
                }
                log.warn(
                        "Order status changed concurrently: orderNumber={}, expectedStatus={},"
                                + " currentStatus={}",
                        orderNumber,
                        currentStatus,
                        latest.getOrderStatus());
                throw new IllegalStateException(
                        "Order status changed while the request was being processed. Please refresh"
                                + " and try again.");
            }
            /*
             * =====================================================
             * PREPARATION SIDE EFFECT: CREATE KOT
             * =====================================================
             */
            if (isStartPreparationTransition(currentStatus, targetStatus)) {
                createPreparationKot(order.getId(), orderNumber);
            }
            log.info(
                    "Admin order status transitioned: orderNumber={}, from={}, to={}",
                    orderNumber,
                    currentStatus,
                    targetStatus);
            return adminOrderQueryService.getOrder(orderNumber);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderWorkflowService.class,
                    "transitionStatus(String,OrderStatus,String)");
        }
    }

    /*
     * =========================================================
     * STRICT BATCH START PREPARATION
     * =========================================================
     *
     * Unlike the general transition method, this returns an
     * explicit operational result so batch processing can
     * distinguish:
     *
     * - this request actually started preparation
     * - another staff member already started it
     * - it is no longer eligible
     * - its status changed
     *
     * Each invocation runs in its own transaction because the
     * batch coordinator itself will NOT have a transaction.
     */
    /**
     * Starts preparation for batch.
     *
     * @param orderNumber the order number
     * @param expectedBranchId the expected branch id
     * @return the start preparation for batch result
     */
    @Transactional
    public PreparationBatchResult startPreparationForBatch(
            String orderNumber, Long expectedBranchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderWorkflowService.class, "startPreparationForBatch(String,Long)");
        try {
            if (orderNumber == null || orderNumber.isBlank()) {
                return PreparationBatchResult.NOT_FOUND;
            }
            Order order = orderRepository.findByOrderNumber(orderNumber.trim()).orElse(null);
            if (order == null) {
                return PreparationBatchResult.NOT_FOUND;
            }
            Long actualBranchId = order.getBranch().getId();
            staffAuthorizationService.requireBranchAccess(actualBranchId);
            staffAuthorizationService.requirePermission(PermissionName.ORDER_START_PREPARATION);
            if (expectedBranchId == null || !actualBranchId.equals(expectedBranchId)) {
                return PreparationBatchResult.BRANCH_MISMATCH;
            }
            OrderStatus currentStatus = order.getOrderStatus();
            if (currentStatus == OrderStatus.PREPARING) {
                return PreparationBatchResult.ALREADY_PREPARING;
            }
            if (currentStatus != OrderStatus.CONFIRMED) {
                return PreparationBatchResult.STATUS_CHANGED;
            }
            PreparationEligibility eligibility = preparationEligibilityService.evaluate(order);
            if (!eligibility.canStartPreparation()) {
                log.debug(
                        "Batch preparation skipped because order is not eligible: orderNumber={},"
                                + " preparationStatus={}, eligibleAt={}",
                        orderNumber,
                        eligibility.status(),
                        eligibility.eligibleAt());
                return PreparationBatchResult.NOT_ELIGIBLE;
            }
            int updatedRows =
                    orderRepository.transitionStatus(
                            order.getId(), OrderStatus.CONFIRMED, OrderStatus.PREPARING);
            /*
             * Another terminal may have won between our read and
             * atomic update.
             */
            if (updatedRows == 0) {
                Order latest = orderRepository.findById(order.getId()).orElse(null);
                if (latest == null) {
                    return PreparationBatchResult.NOT_FOUND;
                }
                if (latest.getOrderStatus() == OrderStatus.PREPARING) {
                    return PreparationBatchResult.ALREADY_PREPARING;
                }
                return PreparationBatchResult.STATUS_CHANGED;
            }
            notifications.orderReady(order.getId());
            createPreparationKot(order.getId(), orderNumber);
            log.info(
                    "Batch preparation started successfully: orderId={}, orderNumber={},"
                            + " branchId={}",
                    order.getId(),
                    orderNumber,
                    actualBranchId);
            return PreparationBatchResult.STARTED;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderWorkflowService.class,
                    "startPreparationForBatch(String,Long)");
        }
    }

    /*
     * =========================================================
     * PREPARATION ELIGIBILITY
     * =========================================================
     */
    /**
     * Requires preparation eligibility.
     *
     * @param order the order
     */
    private void requirePreparationEligibility(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderWorkflowService.class, "requirePreparationEligibility(Order)");
        try {
            PreparationEligibility eligibility = preparationEligibilityService.evaluate(order);
            if (eligibility.canStartPreparation()) {
                return;
            }
            log.warn(
                    "Start preparation rejected because preparation window has not opened:"
                            + " orderId={}, orderNumber={}, preparationStatus={}, eligibleAt={},"
                            + " pickupAt={}",
                    order.getId(),
                    order.getOrderNumber(),
                    eligibility.status(),
                    eligibility.eligibleAt(),
                    eligibility.pickupAt());
            throw new IllegalStateException(
                    "Order is not yet eligible for preparation. Preparation becomes available at "
                            + eligibility.eligibleAt()
                            + ".");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderWorkflowService.class,
                    "requirePreparationEligibility(Order)");
        }
    }

    /*
     * =========================================================
     * PREPARATION KOT
     * =========================================================
     */
    /**
     * Creates preparation kot.
     *
     * @param orderId the order id
     * @param orderNumber the order number
     * @return the create preparation kot result
     */
    private Kot createPreparationKot(Long orderId, String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderWorkflowService.class, "createPreparationKot(Long,String)");
        try {
            Order preparingOrder =
                    orderRepository
                            .findDetailedByOrderNumber(orderNumber)
                            .orElseThrow(
                                    () -> {
                                        log.error(
                                                "Order disappeared after successful PREPARING"
                                                        + " transition: orderId={}, orderNumber={}",
                                                orderId,
                                                orderNumber);
                                        return new IllegalStateException(
                                                "Order could not be reloaded after starting"
                                                        + " preparation.");
                                    });
            Kot kot = kotService.getOrCreateForPreparation(preparingOrder);
            log.info(
                    "Preparation KOT linked to order: orderId={}, orderNumber={}, kotId={},"
                            + " kotNumber={}, startedByStaffId={}, startedByStaffName={}",
                    preparingOrder.getId(),
                    preparingOrder.getOrderNumber(),
                    kot.getId(),
                    kot.getKotNumber(),
                    kot.getStartedByStaffId(),
                    kot.getStartedByStaffName());
            return kot;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderWorkflowService.class,
                    "createPreparationKot(Long,String)");
        }
    }

    /*
     * =========================================================
     * START PREPARATION TRANSITION
     * =========================================================
     */
    /**
     * Reports whether start preparation transition.
     *
     * @param currentStatus the current status
     * @param targetStatus the target status
     * @return the is start preparation transition result
     */
    private boolean isStartPreparationTransition(
            OrderStatus currentStatus, OrderStatus targetStatus) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderWorkflowService.class,
                        "isStartPreparationTransition(OrderStatus,OrderStatus)");
        try {
            return currentStatus == OrderStatus.CONFIRMED && targetStatus == OrderStatus.PREPARING;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderWorkflowService.class,
                    "isStartPreparationTransition(OrderStatus,OrderStatus)");
        }
    }

    /*
     * =========================================================
     * TRANSITION PERMISSIONS
     * =========================================================
     */
    /**
     * Requires permission for transition.
     *
     * @param fulfillmentType the fulfillment type
     * @param currentStatus the current status
     * @param targetStatus the target status
     */
    private void requirePermissionForTransition(
            FulfillmentType fulfillmentType, OrderStatus currentStatus, OrderStatus targetStatus) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderWorkflowService.class,
                        "requirePermissionForTransition(FulfillmentType,OrderStatus,OrderStatus)");
        try {
            if (currentStatus == OrderStatus.CONFIRMED && targetStatus == OrderStatus.PREPARING) {
                staffAuthorizationService.requirePermission(PermissionName.ORDER_START_PREPARATION);
                return;
            }
            if (currentStatus == OrderStatus.PREPARING
                    && (targetStatus == OrderStatus.READY_FOR_PICKUP
                            || targetStatus == OrderStatus.READY_FOR_DELIVERY)) {
                staffAuthorizationService.requirePermission(PermissionName.ORDER_MARK_READY);
                return;
            }
            if (fulfillmentType == FulfillmentType.DELIVERY
                    && currentStatus == OrderStatus.READY_FOR_DELIVERY
                    && targetStatus == OrderStatus.OUT_FOR_DELIVERY) {
                staffAuthorizationService.requirePermission(PermissionName.ORDER_DISPATCH_DELIVERY);
                return;
            }
            if (fulfillmentType == FulfillmentType.DELIVERY
                    && currentStatus == OrderStatus.OUT_FOR_DELIVERY
                    && targetStatus == OrderStatus.DELIVERED) {
                staffAuthorizationService.requirePermission(PermissionName.ORDER_CONFIRM_DELIVERY);
                return;
            }
            if (currentStatus == OrderStatus.READY_FOR_PICKUP
                    && targetStatus == OrderStatus.PICKED_UP) {
                staffAuthorizationService.requirePermission(PermissionName.ORDER_MARK_PICKED_UP);
                return;
            }
            throw new AccessDeniedException(
                    "You do not have permission to perform this transition.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderWorkflowService.class,
                    "requirePermissionForTransition(FulfillmentType,OrderStatus,OrderStatus)");
        }
    }
}
