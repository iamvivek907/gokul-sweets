package com.gokulsweets.restaurant.payment.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.service.RazorpayWebhookService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP endpoints for razorpay webhook operations. */
@RestController
@RequestMapping("/api/payments/webhooks/razorpay")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayWebhookService webhookService;

    /**
     * Handles {@code POST /api/payments/webhooks/razorpay} for razorpay webhook.
     *
     * <p>Delegates to {@code webhookService.process(...)}.
     *
     * @param rawBody the raw body supplied to this method
     * @param signature the signature supplied to this method
     * @param eventId the event id supplied to this method
     * @return the value of {@code ResponseEntity.ok().build()}
     */
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader("X-Razorpay-Event-Id") String eventId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RazorpayWebhookController.class, "receive(byte[],String,String)");
        try {
            webhookService.process(rawBody, signature, eventId);
            return ResponseEntity.ok().build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayWebhookController.class,
                    "receive(byte[],String,String)");
        }
    }
}
