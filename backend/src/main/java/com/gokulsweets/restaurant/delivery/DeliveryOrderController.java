package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.identity.TrustedCheckoutIdentity;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Accepts a recently signed delivery price and creates a pending payment order atomically. */
@RestController
@RequiredArgsConstructor
public class DeliveryOrderController {

    private final EnhancementProperties flags;

    private final DeliveryOrderCreationService orders;

    private final TrustedCheckoutIdentity identity;

    /**
     * Handles {@code POST /api/storefront/delivery/orders} for delivery order.
     *
     * @param idempotencyKey the idempotency key supplied to this method
     * @param request the request supplied to this method
     * @param servletRequest the servlet request supplied to this method
     * @return the {@code ResponseEntity<DeliveryOrderCreationService.Created>} result
     */
    @PostMapping("/api/storefront/delivery/orders")
    public ResponseEntity<DeliveryOrderCreationService.Created> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody DeliveryOrderCreationService.CreateRequest request,
            HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryOrderController.class,
                        "create(String,DeliveryOrderCreationService.CreateRequest,HttpServletRequest)");
        try {
            if (!flags.deliveryCheckoutReady()) return ResponseEntity.notFound().build();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .cacheControl(CacheControl.noStore())
                    .body(orders.create(request, idempotencyKey, identity.token(servletRequest)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryOrderController.class,
                    "create(String,DeliveryOrderCreationService.CreateRequest,HttpServletRequest)");
        }
    }
}
