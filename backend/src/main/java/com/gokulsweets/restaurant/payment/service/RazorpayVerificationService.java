package com.gokulsweets.restaurant.payment.service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayVerificationService {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusService paymentStatusService;
    private final PaymentCheckoutService checkoutService;
    private final RazorpayClient razorpayClient;

    public PaymentResponse verify(
            Long paymentId,
            RazorpayPaymentVerificationRequest request
    ) {
        Payment payment = requirePayment(paymentId);
        requireRazorpayPayment(payment);

        if (!request.razorpayOrderId().equals(
                payment.getProviderOrderId()
        )) {
            throw new IllegalArgumentException(
                    "Razorpay order reference does not match this payment."
            );
        }

        razorpayClient.verifyCheckoutSignature(
                request.razorpayOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature()
        );

        RazorpayClient.ProviderPayment providerPayment =
                razorpayClient.fetchPayment(request.razorpayPaymentId());

        validateProviderPayment(payment, providerPayment);

        if ("captured".equalsIgnoreCase(providerPayment.status())) {
            paymentStatusService.markPaid(
                    payment.getId(),
                    providerPayment.id()
            );
        } else if ("failed".equalsIgnoreCase(providerPayment.status())) {
            paymentStatusService.markFailed(
                    payment.getId(),
                    providerPayment.failureReason()
            );
        }

        log.info(
                "Razorpay checkout verified: paymentId={}, providerStatus={}",
                payment.getId(),
                providerPayment.status()
        );

        return checkoutService.responseFor(payment.getId());
    }

    private void validateProviderPayment(
            Payment local,
            RazorpayClient.ProviderPayment provider
    ) {
        if (!local.getProviderOrderId().equals(provider.orderId())) {
            throw new IllegalArgumentException(
                    "Razorpay payment does not belong to this provider order."
            );
        }

        long expectedAmount = local.getAmount()
                .movePointRight(2)
                .longValueExact();

        if (expectedAmount != provider.amount()) {
            throw new IllegalStateException(
                    "Razorpay payment amount does not match the order amount."
            );
        }
    }

    private Payment requirePayment(Long paymentId) {
        return paymentRepository.findByIdWithOrder(paymentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment does not exist."
                ));
    }

    private void requireRazorpayPayment(Payment payment) {
        if (payment.getProvider() != PaymentProviderType.RAZORPAY) {
            throw new IllegalArgumentException(
                    "Payment is not a Razorpay payment."
            );
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING
                && payment.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException(
                    "This payment can no longer be verified through checkout."
            );
        }
    }
}
