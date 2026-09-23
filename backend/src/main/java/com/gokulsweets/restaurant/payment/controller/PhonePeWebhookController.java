package com.gokulsweets.restaurant.payment.controller;

import com.gokulsweets.restaurant.payment.service.PhonePeCallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/webhooks/phonepe")
@RequiredArgsConstructor
public class PhonePeWebhookController {

    private final PhonePeCallbackService callbackService;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(
                    name = "x-phonepe-checksum-key-id",
                    required = false
            )
            String checksumKeyId,
            @RequestHeader(
                    name = "x-phonepe-checksum-signature",
                    required = false
            )
            String checksumSignature
    ) {
        callbackService.process(
                rawBody,
                checksumKeyId,
                checksumSignature
        );

        return ResponseEntity.ok().build();
    }
}