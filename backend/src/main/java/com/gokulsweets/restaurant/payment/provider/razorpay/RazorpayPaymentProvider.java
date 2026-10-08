package com.gokulsweets.restaurant.payment.provider.razorpay;

import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** Backend razorpay payment provider contract and implementation. */
@Component
@RequiredArgsConstructor
public class RazorpayPaymentProvider implements PaymentProvider {

    private final RazorpayClient client;

    private final RazorpayProperties properties;

    /**
     * Providers type.
     *
     * @return the provider type result
     */
    @Override
    public PaymentProviderType providerType() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayPaymentProvider.class, "providerType()");
        try {
            return PaymentProviderType.RAZORPAY;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayPaymentProvider.class, "providerType()");
        }
    }

    /**
     * Checkouts key id.
     *
     * @return the checkout key id result
     */
    @Override
    public String checkoutKeyId() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayPaymentProvider.class, "checkoutKeyId()");
        try {
            properties.requireApiConfiguration();
            return properties.getKeyId();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayPaymentProvider.class, "checkoutKeyId()");
        }
    }

    /**
     * Creates payment.
     *
     * @param order the order
     * @param payment the payment
     * @return the create payment result
     */
    @Override
    public PaymentCreationResult createPayment(Order order, Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayPaymentProvider.class, "createPayment(Order,Payment)");
        try {
            String receipt = "gks-payment-" + payment.getId();
            RazorpayClient.CreatedOrder created =
                    client.createOrder(
                            payment.getAmount(),
                            payment.getCurrency(),
                            receipt,
                            order.getOrderNumber());
            if (!"created".equalsIgnoreCase(created.status())) {
                throw new IllegalStateException("Razorpay did not create a usable payment order.");
            }
            long expectedAmount = payment.getAmount().movePointRight(2).longValueExact();
            if (created.amount() != expectedAmount) {
                throw new IllegalStateException(
                        "Razorpay order amount does not match the payment amount.");
            }
            return new PaymentCreationResult(null, created.id(), null, null, properties.getKeyId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayPaymentProvider.class,
                    "createPayment(Order,Payment)");
        }
    }

    /**
     * Verify payment.
     *
     * @param payment the payment
     * @return the verify payment result
     */
    @Override
    public PaymentVerificationResult verifyPayment(Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayPaymentProvider.class, "verifyPayment(Payment)");
        try {
            RazorpayClient.ProviderPayment providerPayment;
            if (payment.getProviderPaymentId() != null
                    && !payment.getProviderPaymentId().isBlank()) {
                providerPayment = client.fetchPayment(payment.getProviderPaymentId());
            } else {
                if (payment.getProviderOrderId() == null
                        || payment.getProviderOrderId().isBlank()) {
                    return new PaymentVerificationResult(PaymentStatus.PENDING, null, null);
                }
                providerPayment = client.findBestPaymentForOrder(payment.getProviderOrderId());
            }
            if (providerPayment == null) {
                return new PaymentVerificationResult(PaymentStatus.PENDING, null, null);
            }
            validateProviderPayment(payment, providerPayment);
            return switch (providerPayment.status().toLowerCase()) {
                case "captured" ->
                        new PaymentVerificationResult(
                                PaymentStatus.PAID, providerPayment.id(), null);
                case "failed" ->
                        new PaymentVerificationResult(
                                PaymentStatus.FAILED,
                                providerPayment.id(),
                                providerPayment.failureReason() == null
                                        ? "Razorpay reported that the payment failed."
                                        : providerPayment.failureReason());
                default ->
                        new PaymentVerificationResult(
                                PaymentStatus.PENDING, providerPayment.id(), null);
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayPaymentProvider.class,
                    "verifyPayment(Payment)");
        }
    }

    /**
     * Refunds the operation.
     *
     * @param payment the payment
     * @return the refund result
     */
    @Override
    public RefundResult refund(Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayPaymentProvider.class, "refund(Payment)");
        try {
            requireRefundFields(payment);
            RazorpayClient.ProviderRefund refund =
                    client.createRefund(
                            payment.getProviderPaymentId(),
                            payment.requestedRefundAmount(),
                            payment.getRefundReferenceId());
            return mapRefund(payment, refund);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RazorpayPaymentProvider.class, "refund(Payment)");
        }
    }

    /**
     * Verify refund.
     *
     * @param payment the payment
     * @return the verify refund result
     */
    @Override
    public RefundResult verifyRefund(Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayPaymentProvider.class, "verifyRefund(Payment)");
        try {
            if (payment.getProviderRefundId() == null || payment.getProviderRefundId().isBlank()) {
                throw new IllegalStateException("Razorpay refund ID is missing.");
            }
            requireRefundFields(payment);
            return mapRefund(payment, client.fetchRefund(payment.getProviderRefundId()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayPaymentProvider.class,
                    "verifyRefund(Payment)");
        }
    }

    /**
     * Maps refund.
     *
     * @param payment the payment
     * @param refund the refund
     * @return the map refund result
     */
    private RefundResult mapRefund(Payment payment, RazorpayClient.ProviderRefund refund) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RazorpayPaymentProvider.class,
                        "mapRefund(Payment,RazorpayClient.ProviderRefund)");
        try {
            if (!payment.getProviderPaymentId().equals(refund.paymentId())) {
                throw new IllegalStateException("Razorpay refund belongs to a different payment.");
            }
            long expectedAmount =
                    payment.requestedRefundAmount().movePointRight(2).longValueExact();
            if (refund.amount() != expectedAmount) {
                throw new IllegalStateException(
                        "Razorpay refund amount does not match the requested refund.");
            }
            if (payment.getProviderRefundId() != null
                    && !payment.getProviderRefundId().isBlank()
                    && !payment.getProviderRefundId().equals(refund.id())) {
                throw new IllegalStateException(
                        "Razorpay refund ID does not match the recorded refund.");
            }
            return switch (refund.status().toLowerCase()) {
                case "processed" -> new RefundResult(PaymentStatus.REFUNDED, refund.id(), null);
                case "failed" ->
                        new RefundResult(
                                PaymentStatus.REFUND_FAILED,
                                refund.id(),
                                "Razorpay reported that the refund failed.");
                default -> new RefundResult(PaymentStatus.REFUND_PENDING, refund.id(), null);
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayPaymentProvider.class,
                    "mapRefund(Payment,RazorpayClient.ProviderRefund)");
        }
    }

    /**
     * Requires refund fields.
     *
     * @param payment the payment
     */
    private void requireRefundFields(Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RazorpayPaymentProvider.class, "requireRefundFields(Payment)");
        try {
            if (payment.getProviderPaymentId() == null
                    || payment.getProviderPaymentId().isBlank()
                    || payment.getRefundReferenceId() == null
                    || payment.getRefundReferenceId().isBlank()) {
                throw new IllegalStateException(
                        "Complete Razorpay refund identifiers are required.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayPaymentProvider.class,
                    "requireRefundFields(Payment)");
        }
    }

    /**
     * Validates provider payment.
     *
     * @param payment the payment
     * @param providerPayment the provider payment
     */
    private void validateProviderPayment(
            Payment payment, RazorpayClient.ProviderPayment providerPayment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RazorpayPaymentProvider.class,
                        "validateProviderPayment(Payment,RazorpayClient.ProviderPayment)");
        try {
            if (!payment.getProviderOrderId().equals(providerPayment.orderId())) {
                throw new IllegalStateException(
                        "Razorpay payment belongs to a different provider order.");
            }
            long expectedAmount = payment.getAmount().movePointRight(2).longValueExact();
            if (providerPayment.amount() != expectedAmount) {
                throw new IllegalStateException(
                        "Razorpay payment amount does not match the local payment.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RazorpayPaymentProvider.class,
                    "validateProviderPayment(Payment,RazorpayClient.ProviderPayment)");
        }
    }
}
