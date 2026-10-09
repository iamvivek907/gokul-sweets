package com.gokulsweets.restaurant.order.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.service.OrderDemandService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** HTTP endpoints for order demand operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/orders/planning")
public class OrderDemandController {

    private final OrderDemandService demand;

    /**
     * Handles {@code GET /api/admin/orders/planning/demand} for order demand.
     *
     * @param branchId the branch id supplied to this method
     * @param from the from supplied to this method
     * @param to the to supplied to this method
     * @return the value of {@code demand.get(branchId, from, to)}
     */
    @GetMapping("/demand")
    public List<OrderDemandService.Demand> get(
            @RequestParam long branchId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderDemandController.class, "get(long,LocalDate,LocalDate)");
        try {
            return demand.get(branchId, from, to);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderDemandController.class,
                    "get(long,LocalDate,LocalDate)");
        }
    }

    /**
     * Handles {@code GET /api/admin/orders/planning/demand/export} for order demand.
     *
     * @param branchId the branch id supplied to this method
     * @param from the from supplied to this method
     * @param to the to supplied to this method
     * @return the {@code ResponseEntity<byte[]>} result
     */
    @GetMapping("/demand/export")
    public ResponseEntity<byte[]> export(
            @RequestParam long branchId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderDemandController.class, "export(long,LocalDate,LocalDate)");
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"gokul-demand-"
                                    + branchId
                                    + "-"
                                    + from
                                    + "-"
                                    + to
                                    + ".xlsx\"")
                    .contentType(
                            MediaType.parseMediaType(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(demand.export(branchId, from, to));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderDemandController.class,
                    "export(long,LocalDate,LocalDate)");
        }
    }

    /**
     * Handles {@code GET /api/admin/orders/planning/products} for order demand.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code demand.policies(branchId)}
     */
    @GetMapping("/products")
    public List<OrderDemandService.Policy> policies(@RequestParam long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderDemandController.class, "policies(long)");
        try {
            return demand.policies(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderDemandController.class, "policies(long)");
        }
    }

    /**
     * Immutable policy request data contract.
     *
     * @param earlyPreparationAllowed the early preparation allowed
     */
    public record PolicyRequest(boolean earlyPreparationAllowed) {}

    /**
     * Handles {@code PUT /api/admin/orders/planning/products/{productId}} for order demand.
     *
     * @param productId the product id supplied to this method
     * @param branchId the branch id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code ResponseEntity.noContent().build()}
     */
    @PutMapping("/products/{productId}")
    public ResponseEntity<Void> policy(
            @PathVariable long productId,
            @RequestParam long branchId,
            @RequestBody PolicyRequest input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderDemandController.class, "policy(long,long,PolicyRequest)");
        try {
            demand.policy(branchId, productId, input.earlyPreparationAllowed());
            return ResponseEntity.noContent().build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OrderDemandController.class,
                    "policy(long,long,PolicyRequest)");
        }
    }
}
