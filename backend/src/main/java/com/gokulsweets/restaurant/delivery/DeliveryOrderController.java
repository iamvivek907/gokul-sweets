package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.identity.TrustedCheckoutIdentity;
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

    @PostMapping("/api/storefront/delivery/orders")
    public ResponseEntity<DeliveryOrderCreationService.Created> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody DeliveryOrderCreationService.CreateRequest request,
            HttpServletRequest servletRequest) {
        if (!flags.deliveryCheckoutReady())
            return ResponseEntity.notFound().build();
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(orders.create(request, idempotencyKey, identity.token(servletRequest)));
    }
}
