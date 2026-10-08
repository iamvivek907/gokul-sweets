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
     * Reads the operation.
     *
     * @param orderNumber the order number
     * @param request the request
     * @return the read result
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
     * Selects the operation.
     *
     * @param orderNumber the order number
     * @param selection the selection
     * @param request the request
     * @return the select result
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
