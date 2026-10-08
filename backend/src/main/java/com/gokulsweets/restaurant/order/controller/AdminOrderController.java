package com.gokulsweets.restaurant.order.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.dto.admin.*;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.lifecycle.service.AdminOrderLifecycleCoordinator;
import com.gokulsweets.restaurant.order.service.AdminOrderBatchPreparationService;
import com.gokulsweets.restaurant.order.service.AdminOrderQueryService;
import com.gokulsweets.restaurant.order.service.OrderDelayService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin order operations. */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Slf4j
public class AdminOrderController {

    private final AdminOrderQueryService adminOrderQueryService;

    private final AdminOrderLifecycleCoordinator lifecycleCoordinator;

    private final AdminOrderBatchPreparationService adminOrderBatchPreparationService;

    private final OrderDelayService orderDelayService;

    /**
     * Reports delay.
     *
     * @param orderNumber the order number
     * @param request the request
     * @return the report delay result
     */
    @PatchMapping("/{orderNumber}/delay")
    public ResponseEntity<AdminOrderDetailResponse> reportDelay(
            @PathVariable String orderNumber, @Valid @RequestBody UpdateOrderDelayRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderController.class, "reportDelay(String,UpdateOrderDelayRequest)");
        try {
            return ResponseEntity.ok(orderDelayService.report(orderNumber, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderController.class,
                    "reportDelay(String,UpdateOrderDelayRequest)");
        }
    }

    /**
     * Returns orders.
     *
     * @param branchId the branch id
     * @param status the status
     * @param page the page
     * @param size the size
     * @return the get orders result
     */
    @GetMapping
    public ResponseEntity<AdminOrderPageResponse> getOrders(
            @RequestParam Long branchId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderController.class, "getOrders(Long,OrderStatus,int,int)");
        try {
            return ResponseEntity.ok(
                    adminOrderQueryService.getOrders(branchId, status, page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderController.class,
                    "getOrders(Long,OrderStatus,int,int)");
        }
    }

    /**
     * Returns preparation queue counts.
     *
     * @param branchId the branch id
     * @return the get preparation queue counts result
     */
    @GetMapping("/queue/counts")
    public ResponseEntity<AdminOrderQueueCountsResponse> getPreparationQueueCounts(
            @RequestParam Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminOrderController.class, "getPreparationQueueCounts(Long)");
        try {
            return ResponseEntity.ok(adminOrderQueryService.getPreparationQueueCounts(branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderController.class,
                    "getPreparationQueueCounts(Long)");
        }
    }

    /**
     * Returns preparation queue.
     *
     * @param branchId the branch id
     * @param limit the limit
     * @return the get preparation queue result
     */
    @GetMapping("/queue")
    public ResponseEntity<AdminOrderQueueResponse> getPreparationQueue(
            @RequestParam Long branchId, @RequestParam(required = false) Integer limit) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminOrderController.class, "getPreparationQueue(Long,Integer)");
        try {
            return ResponseEntity.ok(adminOrderQueryService.getPreparationQueue(branchId, limit));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderController.class,
                    "getPreparationQueue(Long,Integer)");
        }
    }

    /**
     * Starts next preparation.
     *
     * @param request the request
     * @return the start next preparation result
     */
    @PostMapping("/queue/start-next")
    public ResponseEntity<AdminBatchPreparationResponse> startNextPreparation(
            @Valid @RequestBody AdminStartNextPreparationRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderController.class,
                        "startNextPreparation(AdminStartNextPreparationRequest)");
        try {
            log.info(
                    "Admin start-next preparation requested: branchId={}, count={}",
                    request.branchId(),
                    request.count());
            return ResponseEntity.ok(adminOrderBatchPreparationService.startNext(request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderController.class,
                    "startNextPreparation(AdminStartNextPreparationRequest)");
        }
    }

    /**
     * Starts selected preparation.
     *
     * @param request the request
     * @return the start selected preparation result
     */
    @PostMapping("/queue/start-selected")
    public ResponseEntity<AdminBatchPreparationResponse> startSelectedPreparation(
            @Valid @RequestBody AdminStartSelectedPreparationRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderController.class,
                        "startSelectedPreparation(AdminStartSelectedPreparationRequest)");
        try {
            log.info(
                    "Admin start-selected preparation requested: branchId={}, count={}",
                    request.branchId(),
                    request.orderNumbers().size());
            return ResponseEntity.ok(adminOrderBatchPreparationService.startSelected(request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderController.class,
                    "startSelectedPreparation(AdminStartSelectedPreparationRequest)");
        }
    }

    /**
     * Returns order by customer number.
     *
     * @param customerOrderNumber the customer order number
     * @return the get order by customer number result
     */
    @GetMapping("/number/{customerOrderNumber}")
    public ResponseEntity<AdminOrderDetailResponse> getOrderByCustomerNumber(
            @PathVariable long customerOrderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminOrderController.class, "getOrderByCustomerNumber(long)");
        try {
            return ResponseEntity.ok(
                    adminOrderQueryService.getOrderByCustomerNumber(customerOrderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderController.class,
                    "getOrderByCustomerNumber(long)");
        }
    }

    /**
     * Returns order.
     *
     * @param orderNumber the order number
     * @return the get order result
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<AdminOrderDetailResponse> getOrder(@PathVariable String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminOrderController.class, "getOrder(String)");
        try {
            return ResponseEntity.ok(adminOrderQueryService.getOrder(orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminOrderController.class, "getOrder(String)");
        }
    }

    /**
     * Updates status.
     *
     * @param orderNumber the order number
     * @param request the request
     * @return the update status result
     */
    @PatchMapping("/{orderNumber}/status")
    public ResponseEntity<AdminOrderDetailResponse> updateStatus(
            @PathVariable String orderNumber,
            @Valid @RequestBody AdminOrderStatusUpdateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderController.class,
                        "updateStatus(String,AdminOrderStatusUpdateRequest)");
        try {
            log.debug(
                    "Admin order status update requested: orderNumber={}, targetStatus={}",
                    orderNumber,
                    request.status());
            return ResponseEntity.ok(
                    lifecycleCoordinator.transitionStatus(
                            orderNumber, request.status(), request.pickupCode()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderController.class,
                    "updateStatus(String,AdminOrderStatusUpdateRequest)");
        }
    }

    /**
     * Cancels order.
     *
     * @param orderNumber the order number
     * @param request the request
     * @return the cancel order result
     */
    @PostMapping("/{orderNumber}/cancel")
    public ResponseEntity<AdminOrderDetailResponse> cancelOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody AdminOrderCancellationRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderController.class,
                        "cancelOrder(String,AdminOrderCancellationRequest)");
        try {
            log.info("Admin unpaid-order cancellation requested: orderNumber={}", orderNumber);
            return ResponseEntity.ok(
                    lifecycleCoordinator.cancelUnpaidOrder(orderNumber, request.reason()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderController.class,
                    "cancelOrder(String,AdminOrderCancellationRequest)");
        }
    }

    /** Immutable pickup code input data contract. */
    public record PickupCodeInput(String pickupCode) {}

    /**
     * Collects late order.
     *
     * @param orderNumber the order number
     * @param input the input
     * @return the collect late order result
     */
    @PostMapping("/{orderNumber}/collect-late")
    public ResponseEntity<AdminOrderDetailResponse> collectLateOrder(
            @PathVariable String orderNumber,
            @RequestBody(required = false) PickupCodeInput input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminOrderController.class, "collectLateOrder(String,PickupCodeInput)");
        try {
            log.info("Admin late pickup requested: orderNumber={}", orderNumber);
            return ResponseEntity.ok(
                    lifecycleCoordinator.collectLateOrder(
                            orderNumber, input == null ? null : input.pickupCode()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminOrderController.class,
                    "collectLateOrder(String,PickupCodeInput)");
        }
    }
}
