package com.gokulsweets.restaurant.payment.controller;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderAccess;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.dto.*;
import com.gokulsweets.restaurant.payment.provider.PaymentProviderRegistry;
import com.gokulsweets.restaurant.payment.service.PaymentCheckoutService;
import com.gokulsweets.restaurant.payment.service.RazorpayVerificationService;

import jakarta.servlet.http.HttpServletRequest;
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

/** HTTP endpoints for payment operations. */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentCheckoutService paymentCheckoutService;

    private final RazorpayVerificationService razorpayVerificationService;

    private final PaymentProviderRegistry providerRegistry;

    private final VerifiedOrderAccess orderAccess;

    /**
     * Providerses the operation.
     *
     * @return the providers result
     */
    @GetMapping("/providers")
    public ResponseEntity<PaymentProviderConfigurationResponse> providers() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentController.class, "providers()");
        try {
            return ResponseEntity.ok(
                    new PaymentProviderConfigurationResponse(
                            providerRegistry.defaultProvider(),
                            providerRegistry.enabledProviders()));
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, PaymentController.class, "providers()");
        }
    }

    /**
     * Creates payment.
     *
     * @param request the request
     * @param servletRequest the servlet request
     * @return the create payment result
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request, HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentController.class,
                        "createPayment(CreatePaymentRequest,HttpServletRequest)");
        try {
            orderAccess.requireOrder(request.orderNumber().trim().toUpperCase(), servletRequest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(
                            paymentCheckoutService.initiatePayment(
                                    request.orderNumber().trim().toUpperCase(),
                                    request.provider()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentController.class,
                    "createPayment(CreatePaymentRequest,HttpServletRequest)");
        }
    }

    /**
     * Returns payment for order.
     *
     * @param orderNumber the order number
     * @param servletRequest the servlet request
     * @return the get payment for order result
     */
    @GetMapping("/order/{orderNumber}")
    public ResponseEntity<PaymentLookupResponse> getPaymentForOrder(
            @PathVariable String orderNumber, HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentController.class, "getPaymentForOrder(String,HttpServletRequest)");
        try {
            orderAccess.requireOrder(orderNumber.trim().toUpperCase(), servletRequest);
            PaymentResponse payment =
                    paymentCheckoutService.findLatestPaymentForOrder(
                            orderNumber.trim().toUpperCase());
            return ResponseEntity.ok(new PaymentLookupResponse(payment));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentController.class,
                    "getPaymentForOrder(String,HttpServletRequest)");
        }
    }

    /**
     * Refreshes payment.
     *
     * @param paymentId the payment id
     * @param servletRequest the servlet request
     * @return the refresh payment result
     */
    @PostMapping("/{paymentId}/refresh")
    public ResponseEntity<PaymentResponse> refreshPayment(
            @PathVariable Long paymentId, HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentController.class, "refreshPayment(Long,HttpServletRequest)");
        try {
            orderAccess.requirePayment(paymentId, servletRequest);
            return ResponseEntity.ok(paymentCheckoutService.refreshPayment(paymentId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentController.class,
                    "refreshPayment(Long,HttpServletRequest)");
        }
    }

    /**
     * Cancels checkout.
     *
     * @param paymentId the payment id
     * @param request the request
     * @return the cancel checkout result
     */
    @PostMapping("/{paymentId}/cancel-checkout")
    public ResponseEntity<PaymentResponse> cancelCheckout(
            @PathVariable Long paymentId, HttpServletRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentController.class, "cancelCheckout(Long,HttpServletRequest)");
        try {
            orderAccess.requirePayment(paymentId, request);
            return ResponseEntity.ok(paymentCheckoutService.cancelCheckout(paymentId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentController.class,
                    "cancelCheckout(Long,HttpServletRequest)");
        }
    }

    /**
     * Verify razorpay payment.
     *
     * @param paymentId the payment id
     * @param request the request
     * @param servletRequest the servlet request
     * @return the verify razorpay payment result
     */
    @PostMapping("/{paymentId}/razorpay/verify")
    public ResponseEntity<PaymentResponse> verifyRazorpayPayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody RazorpayPaymentVerificationRequest request,
            HttpServletRequest servletRequest) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentController.class,
                        "verifyRazorpayPayment(Long,RazorpayPaymentVerificationRequest,HttpServletRequest)");
        try {
            orderAccess.requirePayment(paymentId, servletRequest);
            return ResponseEntity.ok(razorpayVerificationService.verify(paymentId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentController.class,
                    "verifyRazorpayPayment(Long,RazorpayPaymentVerificationRequest,HttpServletRequest)");
        }
    }
}
