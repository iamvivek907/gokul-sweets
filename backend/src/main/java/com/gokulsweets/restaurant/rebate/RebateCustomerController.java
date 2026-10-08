package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.rebate.dto.AppliedRebateResponse;
import com.gokulsweets.restaurant.rebate.dto.ApplyRebateRequest;
import com.gokulsweets.restaurant.rebate.dto.AvailableRebateResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for rebate customer operations. */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class RebateCustomerController {

    private final RebateEligibilityService rebateEligibilityService;

    private final RebateApplicationService rebateApplicationService;

    private final VerifiedOrderAccess orderAccess;

    /**
     * Returns available rebates.
     *
     * @param orderNumber the order number
     * @param servletRequest the servlet request
     * @return the get available rebates result
     */
    @GetMapping("/{orderNumber}/available-rebates")
    public ResponseEntity<List<AvailableRebateResponse>> getAvailableRebates(
            @PathVariable String orderNumber, HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateCustomerController.class,
                        "getAvailableRebates(String,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber, servletRequest);
            return ResponseEntity.ok(rebateEligibilityService.getAvailableRebates(orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateCustomerController.class,
                    "getAvailableRebates(String,HttpServletRequest)");
        }
    }

    /**
     * Spends targets.
     *
     * @param orderNumber the order number
     * @param request the request
     * @return the spend targets result
     */
    @GetMapping("/{orderNumber}/rebate-spend-targets")
    public List<AvailableRebateResponse> spendTargets(
            @PathVariable String orderNumber, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateCustomerController.class, "spendTargets(String,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber, request);
            return rebateEligibilityService.getSpendTargets(orderNumber);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateCustomerController.class,
                    "spendTargets(String,HttpServletRequest)");
        }
    }

    /**
     * Apply rebate.
     *
     * @param orderNumber the order number
     * @param request the request
     * @param servletRequest the servlet request
     * @return the apply rebate result
     */
    @PostMapping("/{orderNumber}/rebate")
    public ResponseEntity<AppliedRebateResponse> applyRebate(
            @PathVariable String orderNumber,
            @Valid @RequestBody ApplyRebateRequest request,
            HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateCustomerController.class,
                        "applyRebate(String,ApplyRebateRequest,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber, servletRequest);
            return ResponseEntity.ok(rebateApplicationService.apply(orderNumber, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateCustomerController.class,
                    "applyRebate(String,ApplyRebateRequest,HttpServletRequest)");
        }
    }

    /**
     * Apply best rebate.
     *
     * @param orderNumber the order number
     * @param request the request
     * @return the apply best rebate result
     */
    @PostMapping("/{orderNumber}/rebate/best")
    public AppliedRebateResponse applyBestRebate(
            @PathVariable String orderNumber, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateCustomerController.class,
                        "applyBestRebate(String,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber, request);
            return rebateApplicationService.applyBest(orderNumber);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateCustomerController.class,
                    "applyBestRebate(String,HttpServletRequest)");
        }
    }

    /**
     * Removes rebate.
     *
     * @param orderNumber the order number
     * @param servletRequest the servlet request
     * @return the remove rebate result
     */
    @DeleteMapping("/{orderNumber}/rebate")
    public ResponseEntity<AppliedRebateResponse> removeRebate(
            @PathVariable String orderNumber, HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateCustomerController.class, "removeRebate(String,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber, servletRequest);
            return ResponseEntity.ok(rebateApplicationService.remove(orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateCustomerController.class,
                    "removeRebate(String,HttpServletRequest)");
        }
    }
}
