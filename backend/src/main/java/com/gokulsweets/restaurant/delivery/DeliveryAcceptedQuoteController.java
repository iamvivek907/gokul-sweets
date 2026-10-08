package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Customer price preview. It does not create an order or reserve a rider or stock. */
@RestController
@RequiredArgsConstructor
public class DeliveryAcceptedQuoteController {

    private final EnhancementProperties flags;

    private final DeliveryAcceptedQuoteService quotes;

    /**
     * Previews the operation.
     *
     * @param request the request
     * @return the preview result
     */
    @PostMapping("/api/storefront/delivery/accepted-quote")
    public ResponseEntity<DeliveryAcceptedQuoteService.Quote> preview(
            @RequestBody DeliveryOrderCreationService.CreateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryAcceptedQuoteController.class,
                        "preview(DeliveryOrderCreationService.CreateRequest)");
        try {
            if (!flags.isDeliveryAcceptedQuote()
                    || !flags.isDeliveryAddressBoundaries()
                    || !flags.isDeliveryRiderHolds()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(quotes.preview(request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryAcceptedQuoteController.class,
                    "preview(DeliveryOrderCreationService.CreateRequest)");
        }
    }
}
