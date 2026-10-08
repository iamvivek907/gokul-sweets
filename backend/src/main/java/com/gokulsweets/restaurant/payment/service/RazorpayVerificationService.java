package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.dto.PaymentResponse;
import com.gokulsweets.restaurant.payment.dto.RazorpayPaymentVerificationRequest;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.provider.razorpay.RazorpayClient;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

/** Coordinates razorpay verification operations. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayVerificationService {

    private final PaymentRepository paymentRepository;

    private final PaymentStatusService paymentStatusService;

    private final PaymentCheckoutService checkoutService;

    private final RazorpayClient razorpayClient;

    /**
     * Verify the operation.
     *
     * @param paymentId the payment id
     * @param request the request
     * @return the verify result
     */
    public PaymentResponse verify(Long paymentId, RazorpayPaymentVerificationRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RazorpayVerificationService.class,
                        "verify(Long,RazorpayPaymentVerificationRequest)");
        try {
            Payment payment = requirePayment(paymentId);
            requireRazorpayPayment(payment);
            if (!request.razorpayOrderId().equals(payment.getProviderOrderId())) {
                throw new IllegalArgumentException(
                        "Razorpay order reference does not match this payment.");
            }
            razorpayClient.verifyCheckoutSignature(
                    request.razorpayOrderId(),
                    request.razorpayPaymentId(),
                    request.razorpaySignature());
            RazorpayClient.ProviderPayment providerPayment =
                    razorpayClient.fetchPayment(request.razorpayPaymentId());
            validateProviderPayment(payment, providerPayment);
            if ("captured".equalsIgnoreCase(providerPayment.status())) {
                paymentStatusService.markPaid(payment.getId(), providerPayment.id());
            } else if ("failed".equalsIgnoreCase(providerPayment.status())) {
                paymentStatusService.markFailed(payment.getId(), providerPayment.failureReason());
            }
            log.info(
                    "Razorpay checkout verified: paymentId={}, providerStatus={}",
                    payment.getId(),
                    providerPayment.status());
            return checkoutService.responseFor(payment.getId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayVerificationService.class,
                    "verify(Long,RazorpayPaymentVerificationRequest)");
        }
    }

    /**
     * Validates provider payment.
     *
     * @param local the local
     * @param provider the provider
     */
    private void validateProviderPayment(Payment local, RazorpayClient.ProviderPayment provider) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RazorpayVerificationService.class,
                        "validateProviderPayment(Payment,RazorpayClient.ProviderPayment)");
        try {
            if (!local.getProviderOrderId().equals(provider.orderId())) {
                throw new IllegalArgumentException(
                        "Razorpay payment does not belong to this provider order.");
            }
            long expectedAmount = local.getAmount().movePointRight(2).longValueExact();
            if (expectedAmount != provider.amount()) {
                throw new IllegalStateException(
                        "Razorpay payment amount does not match the order amount.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayVerificationService.class,
                    "validateProviderPayment(Payment,RazorpayClient.ProviderPayment)");
        }
    }

    /**
     * Requires payment.
     *
     * @param paymentId the payment id
     * @return the require payment result
     */
    private Payment requirePayment(Long paymentId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayVerificationService.class, "requirePayment(Long)");
        try {
            return paymentRepository
                    .findByIdWithOrder(paymentId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment does not exist."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayVerificationService.class,
                    "requirePayment(Long)");
        }
    }

    /**
     * Requires razorpay payment.
     *
     * @param payment the payment
     */
    private void requireRazorpayPayment(Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RazorpayVerificationService.class, "requireRazorpayPayment(Payment)");
        try {
            if (payment.getProvider() != PaymentProviderType.RAZORPAY) {
                throw new IllegalArgumentException("Payment is not a Razorpay payment.");
            }
            if (payment.getPaymentStatus() != PaymentStatus.PENDING
                    && payment.getPaymentStatus() != PaymentStatus.PAID) {
                throw new IllegalStateException(
                        "This payment can no longer be verified through checkout.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayVerificationService.class,
                    "requireRazorpayPayment(Payment)");
        }
    }
}
