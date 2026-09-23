package com.gokulsweets.restaurant.payment.controller;

import com.gokulsweets.restaurant.payment.service.PhonePeCallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/webhooks/phonepe")
@RequiredArgsConstructor
public class PhonePeWebhookController {

    private final PhonePeCallbackService callbackService;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader("X-VERIFY") String signature
    ) {
        callbackService.process(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
