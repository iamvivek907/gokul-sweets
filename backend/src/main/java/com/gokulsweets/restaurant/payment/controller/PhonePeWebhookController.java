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
     * Receives the operation.
     *
     * @param rawBody the raw body
     * @param checksumKeyId the checksum key id
     * @param checksumSignature the checksum signature
     * @return the receive result
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
