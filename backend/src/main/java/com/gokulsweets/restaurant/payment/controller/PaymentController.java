package com.gokulsweets.restaurant.payment.controller;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.payment.dto.*;
import com.gokulsweets.restaurant.payment.provider.PaymentProviderRegistry;
import com.gokulsweets.restaurant.payment.service.PaymentCheckoutService;
import com.gokulsweets.restaurant.payment.service.RazorpayVerificationService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
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
    private final VerifiedOrderAccess orderAccess;


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
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest servletRequest
    ) {

        orderAccess.requireOrder(request.orderNumber().trim().toUpperCase(), servletRequest);

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
    public ResponseEntity<PaymentLookupResponse> getPaymentForOrder(
            @PathVariable String orderNumber,
            HttpServletRequest servletRequest
    ) {
        orderAccess.requireOrder(orderNumber.trim().toUpperCase(), servletRequest);
        PaymentResponse payment =
                paymentCheckoutService.findLatestPaymentForOrder(
                        orderNumber.trim().toUpperCase()
                );

        return ResponseEntity.ok(
                new PaymentLookupResponse(payment)
        );
    }


    @PostMapping("/{paymentId}/refresh")
    public ResponseEntity<PaymentResponse> refreshPayment(
            @PathVariable Long paymentId,
            HttpServletRequest servletRequest
    ) {

        orderAccess.requirePayment(paymentId, servletRequest);

        return ResponseEntity.ok(
                paymentCheckoutService.refreshPayment(
                        paymentId
                )
        );
    }


    @PostMapping("/{paymentId}/cancel-checkout")
    public ResponseEntity<PaymentResponse> cancelCheckout(@PathVariable Long paymentId,HttpServletRequest request) {
        orderAccess.requirePayment(paymentId,request);
        return ResponseEntity.ok(paymentCheckoutService.cancelCheckout(paymentId));
    }

    @PostMapping("/{paymentId}/razorpay/verify")
    public ResponseEntity<PaymentResponse> verifyRazorpayPayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody RazorpayPaymentVerificationRequest request,
            HttpServletRequest servletRequest
    ) {

        orderAccess.requirePayment(paymentId, servletRequest);

        return ResponseEntity.ok(
                razorpayVerificationService.verify(
                        paymentId,
                        request
                )
        );
    }

}
