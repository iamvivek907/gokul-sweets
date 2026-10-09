package com.gokulsweets.restaurant.loyalty;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for loyalty order operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders/{orderNumber}/rewards")
public class LoyaltyOrderController {

    private final VerifiedOrderAccess access;

    private final LoyaltyCheckoutService checkout;

    /**
     * Handles {@code GET /api/orders/{orderNumber}/rewards} for loyalty order.
     *
     * @param orderNumber the order number supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(checkout.read(orderNumber))}
     */
    @GetMapping
    public ResponseEntity<LoyaltyCheckoutService.Checkout> read(
            @PathVariable String orderNumber, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyOrderController.class, "read(String,HttpServletRequest)");
        try {
            access.requireOrder(orderNumber, request);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(checkout.read(orderNumber));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyOrderController.class,
                    "read(String,HttpServletRequest)");
        }
    }

    /**
     * Handles {@code PUT /api/orders/{orderNumber}/rewards} for loyalty order.
     *
     * @param orderNumber the order number supplied to this method
     * @param selection the selection supplied to this method
     * @param request the request supplied to this method
     * @return the value of {@code
     *     ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(checkout.select(orderNumber,
     *     selection.rewardCode(), selection.policyVersion()))}
     */
    @PutMapping
    public ResponseEntity<LoyaltyCheckoutService.Checkout> select(
            @PathVariable String orderNumber,
            @RequestBody LoyaltyService.Selection selection,
            HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        LoyaltyOrderController.class,
                        "select(String,LoyaltyService.Selection,HttpServletRequest)");
        try {
            access.requireOrder(orderNumber, request);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            checkout.select(
                                    orderNumber,
                                    selection.rewardCode(),
                                    selection.policyVersion()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyOrderController.class,
                    "select(String,LoyaltyService.Selection,HttpServletRequest)");
        }
    }
}
