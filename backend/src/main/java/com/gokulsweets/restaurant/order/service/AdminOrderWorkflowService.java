package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.kot.entity.Kot;
import com.gokulsweets.restaurant.kot.service.KotService;
import com.gokulsweets.restaurant.order.dto.admin.AdminOrderDetailResponse;
import com.gokulsweets.restaurant.order.entity.Order;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOrderWorkflowService {

    private static final Map<OrderStatus, OrderStatus>
            ALLOWED_TRANSITIONS =
            Map.of(
                    OrderStatus.CONFIRMED,
                    OrderStatus.PREPARING,

                    OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_PICKUP,

                    OrderStatus.READY_FOR_PICKUP,
                    OrderStatus.PICKED_UP
            );


    private final OrderRepository
            orderRepository;


    private final AdminOrderQueryService
            adminOrderQueryService;


    private final StaffAuthorizationService
            staffAuthorizationService;


    private final KotService
            kotService;


    private final PreparationEligibilityService
            preparationEligibilityService;


    /*
     * =========================================================
     * STANDARD SINGLE-ORDER STATUS TRANSITION
     * =========================================================
     */

    @Transactional
    public AdminOrderDetailResponse transitionStatus(
            String orderNumber,
            OrderStatus targetStatus
    ) {

        Order order =
                orderRepository
                        .findByOrderNumber(
                                orderNumber
                        )
                        .orElseThrow(() -> {

                            log.warn(
                                    "Admin status transition failed because order does not exist: orderNumber={}",
                                    orderNumber
                            );

                            return new IllegalArgumentException(
                                    "Order does not exist."
                            );
                        });


        staffAuthorizationService
                .requireBranchAccess(
                        order.getBranch()
                                .getId()
                );


        OrderStatus currentStatus =
                order.getOrderStatus();


        /*
         * =====================================================
         * IDEMPOTENT DUPLICATE REQUEST
         * =====================================================
         */

        if (
                currentStatus
                        ==
                        targetStatus
        ) {

            log.debug(
                    "Ignoring duplicate admin order transition: orderNumber={}, status={}",
                    orderNumber,
                    currentStatus
            );


            return adminOrderQueryService
                    .getOrder(
                            orderNumber
                    );
        }


        /*
         * =====================================================
         * BUSINESS WORKFLOW VALIDATION
         * =====================================================
         */

        OrderStatus allowedTarget =
                ALLOWED_TRANSITIONS.get(
                        currentStatus
                );


        if (
                allowedTarget
                        ==
                        null
                        ||
                        allowedTarget
                                !=
                                targetStatus
        ) {

            log.warn(
                    "Invalid admin order transition: orderNumber={}, currentStatus={}, requestedStatus={}",
                    orderNumber,
                    currentStatus,
                    targetStatus
            );


            throw new IllegalStateException(
                    "Order cannot move from "
                            + currentStatus
                            + " to "
                            + targetStatus
                            + "."
            );
        }


        /*
         * =====================================================
         * TRANSITION-SPECIFIC PERMISSION
         * =====================================================
         */

        requirePermissionForTransition(
                currentStatus,
                targetStatus
        );


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

        if (
                isStartPreparationTransition(
                        currentStatus,
                        targetStatus
                )
        ) {

            requirePreparationEligibility(
                    order
            );
        }


        /*
         * =====================================================
         * ATOMIC ORDER STATUS TRANSITION
         * =====================================================
         */

        int updatedRows =
                orderRepository
                        .transitionStatus(
                                order.getId(),
                                currentStatus,
                                targetStatus
                        );


        /*
         * =====================================================
         * CONCURRENT UPDATE
         * =====================================================
         */

        if (
                updatedRows
                        ==
                        0
        ) {

            Order latest =
                    orderRepository
                            .findById(
                                    order.getId()
                            )
                            .orElseThrow();


            if (
                    latest.getOrderStatus()
                            ==
                            targetStatus
            ) {

                log.debug(
                        "Order transition already completed by another request: orderNumber={}, status={}",
                        orderNumber,
                        targetStatus
                );


                return adminOrderQueryService
                        .getOrder(
                                orderNumber
                        );
            }


            log.warn(
                    "Order status changed concurrently: orderNumber={}, expectedStatus={}, currentStatus={}",
                    orderNumber,
                    currentStatus,
                    latest.getOrderStatus()
            );


            throw new IllegalStateException(
                    "Order status changed while the request was being processed. Please refresh and try again."
            );
        }


        /*
         * =====================================================
         * PREPARATION SIDE EFFECT: CREATE KOT
         * =====================================================
         */

        if (
                isStartPreparationTransition(
                        currentStatus,
                        targetStatus
                )
        ) {

            createPreparationKot(
                    order.getId(),
                    orderNumber
            );
        }


        log.info(
                "Admin order status transitioned: orderNumber={}, from={}, to={}",
                orderNumber,
                currentStatus,
                targetStatus
        );


        return adminOrderQueryService
                .getOrder(
                        orderNumber
                );
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

    @Transactional
    public PreparationBatchResult startPreparationForBatch(
            String orderNumber,
            Long expectedBranchId
    ) {

        if (
                orderNumber == null
                        ||
                        orderNumber.isBlank()
        ) {

            return PreparationBatchResult.NOT_FOUND;
        }


        Order order =
                orderRepository
                        .findByOrderNumber(
                                orderNumber.trim()
                        )
                        .orElse(null);


        if (order == null) {

            return PreparationBatchResult.NOT_FOUND;
        }


        Long actualBranchId =
                order.getBranch()
                        .getId();


        staffAuthorizationService
                .requireBranchAccess(
                        actualBranchId
                );


        staffAuthorizationService
                .requirePermission(
                        PermissionName.ORDER_START_PREPARATION
                );


        if (
                expectedBranchId == null
                        ||
                        !actualBranchId.equals(
                                expectedBranchId
                        )
        ) {

            return PreparationBatchResult.BRANCH_MISMATCH;
        }


        OrderStatus currentStatus =
                order.getOrderStatus();


        if (
                currentStatus
                        ==
                        OrderStatus.PREPARING
        ) {

            return PreparationBatchResult.ALREADY_PREPARING;
        }


        if (
                currentStatus
                        !=
                        OrderStatus.CONFIRMED
        ) {

            return PreparationBatchResult.STATUS_CHANGED;
        }


        PreparationEligibility eligibility =
                preparationEligibilityService
                        .evaluate(
                                order
                        );


        if (
                !eligibility
                        .canStartPreparation()
        ) {

            log.debug(
                    "Batch preparation skipped because order is not eligible: orderNumber={}, preparationStatus={}, eligibleAt={}",
                    orderNumber,
                    eligibility.status(),
                    eligibility.eligibleAt()
            );


            return PreparationBatchResult.NOT_ELIGIBLE;
        }


        int updatedRows =
                orderRepository
                        .transitionStatus(
                                order.getId(),
                                OrderStatus.CONFIRMED,
                                OrderStatus.PREPARING
                        );


        /*
         * Another terminal may have won between our read and
         * atomic update.
         */
        if (
                updatedRows
                        ==
                        0
        ) {

            Order latest =
                    orderRepository
                            .findById(
                                    order.getId()
                            )
                            .orElse(null);


            if (latest == null) {

                return PreparationBatchResult.NOT_FOUND;
            }


            if (
                    latest.getOrderStatus()
                            ==
                            OrderStatus.PREPARING
            ) {

                return PreparationBatchResult.ALREADY_PREPARING;
            }


            return PreparationBatchResult.STATUS_CHANGED;
        }


        createPreparationKot(
                order.getId(),
                orderNumber
        );


        log.info(
                "Batch preparation started successfully: orderId={}, orderNumber={}, branchId={}",
                order.getId(),
                orderNumber,
                actualBranchId
        );


        return PreparationBatchResult.STARTED;
    }


    /*
     * =========================================================
     * PREPARATION ELIGIBILITY
     * =========================================================
     */

    private void requirePreparationEligibility(
            Order order
    ) {

        PreparationEligibility eligibility =
                preparationEligibilityService
                        .evaluate(
                                order
                        );


        if (
                eligibility
                        .canStartPreparation()
        ) {

            return;
        }


        log.warn(
                "Start preparation rejected because preparation window has not opened: orderId={}, orderNumber={}, preparationStatus={}, eligibleAt={}, pickupAt={}",
                order.getId(),
                order.getOrderNumber(),
                eligibility.status(),
                eligibility.eligibleAt(),
                eligibility.pickupAt()
        );


        throw new IllegalStateException(
                "Order is not yet eligible for preparation. Preparation becomes available at "
                        +
                        eligibility.eligibleAt()
                        +
                        "."
        );
    }


    /*
     * =========================================================
     * PREPARATION KOT
     * =========================================================
     */

    private Kot createPreparationKot(
            Long orderId,
            String orderNumber
    ) {

        Order preparingOrder =
                orderRepository
                        .findDetailedByOrderNumber(
                                orderNumber
                        )
                        .orElseThrow(() -> {

                            log.error(
                                    "Order disappeared after successful PREPARING transition: orderId={}, orderNumber={}",
                                    orderId,
                                    orderNumber
                            );


                            return new IllegalStateException(
                                    "Order could not be reloaded after starting preparation."
                            );
                        });


        Kot kot =
                kotService
                        .getOrCreateForPreparation(
                                preparingOrder
                        );


        log.info(
                "Preparation KOT linked to order: orderId={}, orderNumber={}, kotId={}, kotNumber={}, startedByStaffId={}, startedByStaffName={}",
                preparingOrder.getId(),
                preparingOrder.getOrderNumber(),
                kot.getId(),
                kot.getKotNumber(),
                kot.getStartedByStaffId(),
                kot.getStartedByStaffName()
        );


        return kot;
    }


    /*
     * =========================================================
     * START PREPARATION TRANSITION
     * =========================================================
     */

    private boolean isStartPreparationTransition(
            OrderStatus currentStatus,
            OrderStatus targetStatus
    ) {

        return currentStatus
                ==
                OrderStatus.CONFIRMED
                &&
                targetStatus
                        ==
                        OrderStatus.PREPARING;
    }


    /*
     * =========================================================
     * TRANSITION PERMISSIONS
     * =========================================================
     */

    private void requirePermissionForTransition(
            OrderStatus currentStatus,
            OrderStatus targetStatus
    ) {

        if (
                currentStatus
                        ==
                        OrderStatus.CONFIRMED
                        &&
                        targetStatus
                                ==
                                OrderStatus.PREPARING
        ) {

            staffAuthorizationService
                    .requirePermission(
                            PermissionName.ORDER_START_PREPARATION
                    );

            return;
        }


        if (
                currentStatus
                        ==
                        OrderStatus.PREPARING
                        &&
                        targetStatus
                                ==
                                OrderStatus.READY_FOR_PICKUP
        ) {

            staffAuthorizationService
                    .requirePermission(
                            PermissionName.ORDER_MARK_READY
                    );

            return;
        }


        if (
                currentStatus
                        ==
                        OrderStatus.READY_FOR_PICKUP
                        &&
                        targetStatus
                                ==
                                OrderStatus.PICKED_UP
        ) {

            staffAuthorizationService
                    .requirePermission(
                            PermissionName.ORDER_MARK_PICKED_UP
                    );

            return;
        }


        throw new AccessDeniedException(
                "You do not have permission to perform this transition."
        );
    }
}