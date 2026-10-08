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
     * Returns the operation.
     *
     * @param branchId the branch id
     * @param from the from
     * @param to the to
     * @return the get result
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
     * Exports the operation.
     *
     * @param branchId the branch id
     * @param from the from
     * @param to the to
     * @return the export result
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
     * Policieses the operation.
     *
     * @param branchId the branch id
     * @return the policies result
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
     * Policy the operation.
     *
     * @param productId the product id
     * @param branchId the branch id
     * @param input the input
     * @return the policy result
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
