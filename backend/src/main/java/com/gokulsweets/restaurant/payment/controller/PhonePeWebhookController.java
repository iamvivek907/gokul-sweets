package com.gokulsweets.restaurant.payment.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.service.PhonePeCallbackService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for phone pe webhook operations. */
@RestController
@RequestMapping("/api/payments/webhooks/phonepe")
@RequiredArgsConstructor
public class PhonePeWebhookController {

    private final PhonePeCallbackService callbackService;

    /**
     * Handles {@code POST /api/payments/webhooks/phonepe} for phone pe webhook.
     *
     * <p>Delegates to {@code callbackService.process(...)}.
     *
     * @param rawBody the raw body supplied to this method
     * @param checksumKeyId the checksum key id supplied to this method
     * @param checksumSignature the checksum signature supplied to this method
     * @return the value of {@code ResponseEntity.ok().build()}
     */
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(name = "x-phonepe-checksum-key-id", required = false)
                    String checksumKeyId,
            @RequestHeader(name = "x-phonepe-checksum-signature", required = false)
                    String checksumSignature) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePeWebhookController.class, "receive(byte[],String,String)");
        try {
            callbackService.process(rawBody, checksumKeyId, checksumSignature);
            return ResponseEntity.ok().build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePeWebhookController.class,
                    "receive(byte[],String,String)");
        }
    }
}
