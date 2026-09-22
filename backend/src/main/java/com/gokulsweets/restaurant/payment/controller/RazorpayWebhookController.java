package com.gokulsweets.restaurant.payment.controller;

import com.gokulsweets.restaurant.payment.service.RazorpayWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/webhooks/razorpay")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpayWebhookService webhookService;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader("X-Razorpay-Event-Id") String eventId
    ) {
        webhookService.process(rawBody, signature, eventId);
        return ResponseEntity.ok().build();
    }
}
