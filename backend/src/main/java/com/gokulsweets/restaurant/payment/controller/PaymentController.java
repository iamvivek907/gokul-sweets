package com.gokulsweets.restaurant.payment.controller;

import com.gokulsweets.restaurant.payment.dto.CreatePaymentRequest;
import com.gokulsweets.restaurant.payment.dto.PaymentProviderConfigurationResponse;
import com.gokulsweets.restaurant.payment.dto.PaymentResponse;
import com.gokulsweets.restaurant.payment.dto.RazorpayPaymentVerificationRequest;
import com.gokulsweets.restaurant.payment.provider.PaymentProviderRegistry;
import com.gokulsweets.restaurant.payment.service.PaymentCheckoutService;
import com.gokulsweets.restaurant.payment.service.RazorpayVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentCheckoutService paymentCheckoutService;
    private final RazorpayVerificationService razorpayVerificationService;
    private final PaymentProviderRegistry providerRegistry;


    @GetMapping("/providers")
    public ResponseEntity<PaymentProviderConfigurationResponse> providers() {

        return ResponseEntity.ok(
                new PaymentProviderConfigurationResponse(
                        providerRegistry.defaultProvider(),
                        providerRegistry.enabledProviders()
                )
        );
    }


    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        paymentCheckoutService.initiatePayment(
                                request.orderNumber()
                                        .trim()
                                        .toUpperCase(),
                                request.provider()
                        )
                );
    }


    /*
     * =========================================================
     * RECOVER PAYMENT BY ORDER NUMBER
     * =========================================================
     */
    @GetMapping("/order/{orderNumber}")
    public ResponseEntity<PaymentResponse> getPaymentForOrder(
            @PathVariable String orderNumber
    ) {

        return ResponseEntity.ok(
                paymentCheckoutService.getLatestPaymentForOrder(
                        orderNumber
                                .trim()
                                .toUpperCase()
                )
        );
    }


    @PostMapping("/{paymentId}/refresh")
    public ResponseEntity<PaymentResponse> refreshPayment(
            @PathVariable Long paymentId
    ) {

        return ResponseEntity.ok(
                paymentCheckoutService.refreshPayment(
                        paymentId
                )
        );
    }


    @PostMapping("/{paymentId}/razorpay/verify")
    public ResponseEntity<PaymentResponse> verifyRazorpayPayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody RazorpayPaymentVerificationRequest request
    ) {

        return ResponseEntity.ok(
                razorpayVerificationService.verify(
                        paymentId,
                        request
                )
        );
    }
}