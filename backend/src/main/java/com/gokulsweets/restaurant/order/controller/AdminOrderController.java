package com.gokulsweets.restaurant.order.controller;

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

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Slf4j
public class AdminOrderController {

    private final AdminOrderQueryService adminOrderQueryService;
    private final AdminOrderLifecycleCoordinator lifecycleCoordinator;
    private final AdminOrderBatchPreparationService adminOrderBatchPreparationService;
    private final OrderDelayService orderDelayService;

    @PatchMapping("/{orderNumber}/delay")
    public ResponseEntity<AdminOrderDetailResponse> reportDelay(
            @PathVariable String orderNumber,
            @Valid @RequestBody UpdateOrderDelayRequest request
    ) {
        return ResponseEntity.ok(orderDelayService.report(orderNumber, request));
    }

    @GetMapping
    public ResponseEntity<AdminOrderPageResponse> getOrders(
            @RequestParam Long branchId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                adminOrderQueryService.getOrders(branchId, status, page, size)
        );
    }

    @GetMapping("/queue/counts")
    public ResponseEntity<AdminOrderQueueCountsResponse> getPreparationQueueCounts(
            @RequestParam Long branchId
    ) {
        return ResponseEntity.ok(
                adminOrderQueryService.getPreparationQueueCounts(branchId)
        );
    }

    @GetMapping("/queue")
    public ResponseEntity<AdminOrderQueueResponse> getPreparationQueue(
            @RequestParam Long branchId,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(
                adminOrderQueryService.getPreparationQueue(branchId, limit)
        );
    }

    @PostMapping("/queue/start-next")
    public ResponseEntity<AdminBatchPreparationResponse> startNextPreparation(
            @Valid @RequestBody AdminStartNextPreparationRequest request
    ) {
        log.info("Admin start-next preparation requested: branchId={}, count={}",
                request.branchId(), request.count());
        return ResponseEntity.ok(
                adminOrderBatchPreparationService.startNext(request)
        );
    }

    @PostMapping("/queue/start-selected")
    public ResponseEntity<AdminBatchPreparationResponse> startSelectedPreparation(
            @Valid @RequestBody AdminStartSelectedPreparationRequest request
    ) {
        log.info("Admin start-selected preparation requested: branchId={}, count={}",
                request.branchId(), request.orderNumbers().size());
        return ResponseEntity.ok(
                adminOrderBatchPreparationService.startSelected(request)
        );
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<AdminOrderDetailResponse> getOrder(
            @PathVariable String orderNumber
    ) {
        return ResponseEntity.ok(adminOrderQueryService.getOrder(orderNumber));
    }

    @PatchMapping("/{orderNumber}/status")
    public ResponseEntity<AdminOrderDetailResponse> updateStatus(
            @PathVariable String orderNumber,
            @Valid @RequestBody AdminOrderStatusUpdateRequest request
    ) {
        log.debug("Admin order status update requested: orderNumber={}, targetStatus={}",
                orderNumber, request.status());
        return ResponseEntity.ok(
                lifecycleCoordinator.transitionStatus(orderNumber, request.status())
        );
    }

    @PostMapping("/{orderNumber}/cancel")
    public ResponseEntity<AdminOrderDetailResponse> cancelOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody AdminOrderCancellationRequest request
    ) {
        log.info("Admin unpaid-order cancellation requested: orderNumber={}", orderNumber);
        return ResponseEntity.ok(
                lifecycleCoordinator.cancelUnpaidOrder(orderNumber, request.reason())
        );
    }

    @PostMapping("/{orderNumber}/collect-late")
    public ResponseEntity<AdminOrderDetailResponse> collectLateOrder(
            @PathVariable String orderNumber
    ) {
        log.info("Admin late pickup requested: orderNumber={}", orderNumber);
        return ResponseEntity.ok(
                lifecycleCoordinator.collectLateOrder(orderNumber)
        );
    }
}
