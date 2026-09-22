package com.gokulsweets.restaurant.payment.provider.razorpay;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.provider.PaymentCreationResult;
import com.gokulsweets.restaurant.payment.provider.PaymentProvider;
import com.gokulsweets.restaurant.payment.provider.PaymentVerificationResult;
import com.gokulsweets.restaurant.payment.provider.RefundResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RazorpayPaymentProvider implements PaymentProvider {

    private final RazorpayClient client;
    private final RazorpayProperties properties;

    @Override
    public PaymentProviderType providerType() {
        return PaymentProviderType.RAZORPAY;
    }

    @Override
    public String checkoutKeyId() {
        properties.requireApiConfiguration();
        return properties.getKeyId();
    }

    @Override
    public PaymentCreationResult createPayment(
            Order order,
            Payment payment
    ) {
        String receipt = "gks-payment-" + payment.getId();

        RazorpayClient.CreatedOrder created = client.createOrder(
                payment.getAmount(),
                payment.getCurrency(),
                receipt,
                order.getOrderNumber()
        );

        if (!"created".equalsIgnoreCase(created.status())) {
            throw new IllegalStateException(
                    "Razorpay did not create a usable payment order."
            );
        }

        long expectedAmount = payment.getAmount()
                .movePointRight(2)
                .longValueExact();
        if (created.amount() != expectedAmount) {
            throw new IllegalStateException(
                    "Razorpay order amount does not match the payment amount."
            );
        }

        return new PaymentCreationResult(
                null,
                created.id(),
                null,
                null,
                properties.getKeyId()
        );
    }

    @Override
    public PaymentVerificationResult verifyPayment(Payment payment) {
        RazorpayClient.ProviderPayment providerPayment;

        if (payment.getProviderPaymentId() != null
                && !payment.getProviderPaymentId().isBlank()) {
            providerPayment = client.fetchPayment(
                    payment.getProviderPaymentId()
            );
        } else {
            if (payment.getProviderOrderId() == null
                    || payment.getProviderOrderId().isBlank()) {
                return new PaymentVerificationResult(
                        PaymentStatus.PENDING,
                        null,
                        null
                );
            }
            providerPayment = client.findBestPaymentForOrder(
                    payment.getProviderOrderId()
            );
        }

        if (providerPayment == null) {
            return new PaymentVerificationResult(
                    PaymentStatus.PENDING,
                    null,
                    null
            );
        }

        validateProviderPayment(payment, providerPayment);

        return switch (providerPayment.status().toLowerCase()) {
            case "captured" -> new PaymentVerificationResult(
                    PaymentStatus.PAID,
                    providerPayment.id(),
                    null
            );
            case "failed" -> new PaymentVerificationResult(
                    PaymentStatus.FAILED,
                    providerPayment.id(),
                    providerPayment.failureReason() == null
                            ? "Razorpay reported that the payment failed."
                            : providerPayment.failureReason()
            );
            default -> new PaymentVerificationResult(
                    PaymentStatus.PENDING,
                    providerPayment.id(),
                    null
            );
        };
    }

    @Override
    public RefundResult refund(Payment payment) {
        requireRefundFields(payment);
        RazorpayClient.ProviderRefund refund = client.createRefund(
                payment.getProviderPaymentId(),
                payment.getAmount(),
                payment.getRefundReferenceId()
        );
        return mapRefund(refund);
    }

    @Override
    public RefundResult verifyRefund(Payment payment) {
        if (payment.getProviderRefundId() == null
                || payment.getProviderRefundId().isBlank()) {
            throw new IllegalStateException(
                    "Razorpay refund ID is missing."
            );
        }
        return mapRefund(client.fetchRefund(
                payment.getProviderRefundId()
        ));
    }

    private RefundResult mapRefund(
            RazorpayClient.ProviderRefund refund
    ) {
        return switch (refund.status().toLowerCase()) {
            case "processed" -> new RefundResult(
                    PaymentStatus.REFUNDED,
                    refund.id(),
                    null
            );
            case "failed" -> new RefundResult(
                    PaymentStatus.REFUND_FAILED,
                    refund.id(),
                    "Razorpay reported that the refund failed."
            );
            default -> new RefundResult(
                    PaymentStatus.REFUND_PENDING,
                    refund.id(),
                    null
            );
        };
    }

    private void requireRefundFields(Payment payment) {
        if (payment.getProviderPaymentId() == null
                || payment.getProviderPaymentId().isBlank()
                || payment.getRefundReferenceId() == null
                || payment.getRefundReferenceId().isBlank()) {
            throw new IllegalStateException(
                    "Complete Razorpay refund identifiers are required."
            );
        }
    }

    private void validateProviderPayment(
            Payment payment,
            RazorpayClient.ProviderPayment providerPayment
    ) {
        if (!payment.getProviderOrderId().equals(
                providerPayment.orderId()
        )) {
            throw new IllegalStateException(
                    "Razorpay payment belongs to a different provider order."
            );
        }

        long expectedAmount = payment.getAmount()
                .movePointRight(2)
                .longValueExact();
        if (providerPayment.amount() != expectedAmount) {
            throw new IllegalStateException(
                    "Razorpay payment amount does not match the local payment."
            );
        }
    }
}
