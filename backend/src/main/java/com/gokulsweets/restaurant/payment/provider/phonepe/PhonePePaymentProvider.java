package com.gokulsweets.restaurant.payment.provider.phonepe;

import com.gokulsweets.restaurant.config.EnvironmentIsolationGuard;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Backend phone pe payment provider contract and implementation. */
@Component
@RequiredArgsConstructor
public class PhonePePaymentProvider implements PaymentProvider {

    private final PhonePeClient client;

    private final PhonePeProperties properties;

    private final EnvironmentIsolationGuard isolationGuard;

    /**
     * Providers type.
     *
     * @return the provider type result
     */
    @Override
    public PaymentProviderType providerType() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePePaymentProvider.class, "providerType()");
        try {
            return PaymentProviderType.PHONEPE;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePePaymentProvider.class, "providerType()");
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
                MethodTiming.start(PhonePePaymentProvider.class, "createPayment(Order,Payment)");
        try {
            properties.requireApiConfiguration();
            properties.requireRedirectConfiguration();
            /*
             * This is our own merchant-side identifier.
             *
             * It is stored in Payment.providerOrderId and must be used
             * for all subsequent PhonePe status checks.
             */
            String merchantOrderId = isolationGuard.phonePeMerchantOrderId(payment.getId());
            /*
             * PhonePe redirects the customer back to our payment page.
             *
             * Example:
             * https://dev.gokulsweets.in/checkout/payment/GKS-20260923-490EAC17BE04497A
             */
            String redirectUrl = buildRedirectUrl(order.getOrderNumber());
            PhonePeClient.CreatePaymentResponse created =
                    client.createPayment(merchantOrderId, payment.getAmount(), redirectUrl);
            if (created.orderId() == null || created.orderId().isBlank()) {
                throw new IllegalStateException(
                        created.message() == null || created.message().isBlank()
                                ? "PhonePe did not return a PhonePe order ID."
                                : created.message());
            }
            if (created.redirectUrl() == null || created.redirectUrl().isBlank()) {
                throw new IllegalStateException(
                        created.message() == null || created.message().isBlank()
                                ? "PhonePe did not return a checkout URL."
                                : created.message());
            }
            /*
             * Mapping:
             *
             * providerPaymentId = PhonePe generated orderId
             * providerOrderId   = our merchantOrderId
             * paymentSessionId  = not used by PhonePe V2
             * paymentUrl        = PhonePe redirectUrl
             * checkoutKeyId     = not used
             */
            return new PaymentCreationResult(
                    created.orderId(),
                    created.merchantOrderId(),
                    null,
                    created.redirectUrl(),
                    null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePePaymentProvider.class,
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
                MethodTiming.start(PhonePePaymentProvider.class, "verifyPayment(Payment)");
        try {
            String merchantOrderId = requireProviderOrderId(payment);
            PhonePeClient.StatusResponse status = client.checkStatus(merchantOrderId);
            String state = status.state() == null ? "PENDING" : status.state().trim().toUpperCase();
            return switch (state) {
                case "COMPLETED" ->
                        new PaymentVerificationResult(
                                PaymentStatus.PAID, status.transactionId(), null);
                case "FAILED" ->
                        new PaymentVerificationResult(
                                PaymentStatus.FAILED,
                                status.transactionId(),
                                status.errorMessage() == null || status.errorMessage().isBlank()
                                        ? "PhonePe reported that the payment failed."
                                        : status.errorMessage());
                default ->
                        new PaymentVerificationResult(
                                PaymentStatus.PENDING, status.transactionId(), null);
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePePaymentProvider.class,
                    "verifyPayment(Payment)");
        }
    }

    /**
     * Refunds phone pe payment provider data and returns the {@code RefundResult} result.
     *
     * <p>Delegates to {@code client.refundStatus(...)}, {@code client.refund(...)}.
     *
     * @param payment the payment supplied to this method
     * @return the {@code RefundResult} result
     */
    @Override
    public RefundResult refund(Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePePaymentProvider.class, "refund(Payment)");
        try {
            requireRefundReference(payment);
            // Only new, durably marked submissions skip the lookup of a nonexistent refund.
            // Legacy pending refunds and lost acknowledgements must check first.
            if (payment.getRefundSubmissionAttemptedAt() != null
                    || payment.getProviderRefundId() != null) {
                try {
                    return mapRefund(
                            payment, client.refundStatus(payment.getRefundReferenceId()), true);
                } catch (
                        com.gokulsweets.restaurant.payment.exception.PaymentGatewayException
                                missing) {
                    if (!"PHONEPE_REFUND_NOT_FOUND".equals(missing.getCode())) throw missing;
                }
            }
            return mapRefund(
                    payment,
                    client.refund(
                            payment.getRefundReferenceId(),
                            requireProviderOrderId(payment),
                            payment.requestedRefundAmount()),
                    false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PhonePePaymentProvider.class, "refund(Payment)");
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
                MethodTiming.start(PhonePePaymentProvider.class, "verifyRefund(Payment)");
        try {
            requireRefundReference(payment);
            return mapRefund(payment, client.refundStatus(payment.getRefundReferenceId()), true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePePaymentProvider.class,
                    "verifyRefund(Payment)");
        }
    }

    /**
     * Verify refund callback.
     *
     * @param payment the payment
     * @param response the response
     * @return the verify refund callback result
     */
    public RefundResult verifyRefundCallback(
            Payment payment, PhonePeClient.RefundResponse response) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PhonePePaymentProvider.class,
                        "verifyRefundCallback(Payment,PhonePeClient.RefundResponse)");
        try {
            requireRefundReference(payment);
            return mapRefund(payment, response, true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePePaymentProvider.class,
                    "verifyRefundCallback(Payment,PhonePeClient.RefundResponse)");
        }
    }

    /**
     * Requires refund reference.
     *
     * @param payment the payment
     */
    private void requireRefundReference(Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePePaymentProvider.class, "requireRefundReference(Payment)");
        try {
            if (payment.getRefundReferenceId() == null || payment.getRefundReferenceId().isBlank())
                throw new IllegalStateException("Refund reference is missing.");
            requireProviderOrderId(payment);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePePaymentProvider.class,
                    "requireRefundReference(Payment)");
        }
    }

    /**
     * Maps refund.
     *
     * @param payment the payment
     * @param result the result
     * @param status the status
     * @return the map refund result
     */
    private RefundResult mapRefund(
            Payment payment, PhonePeClient.RefundResponse result, boolean status) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PhonePePaymentProvider.class,
                        "mapRefund(Payment,PhonePeClient.RefundResponse,boolean)");
        try {
            long expected = payment.requestedRefundAmount().movePointRight(2).longValueExact();
            if (result.amount() != expected
                    || result.refundId() == null
                    || result.refundId().isBlank()
                    || result.merchantRefundId() != null
                            && !payment.getRefundReferenceId().equals(result.merchantRefundId())
                    || status
                            && !requireProviderOrderId(payment)
                                    .equals(result.originalMerchantOrderId())
                    || !status
                            && result.originalMerchantOrderId() != null
                            && !requireProviderOrderId(payment)
                                    .equals(result.originalMerchantOrderId())
                    || payment.getProviderRefundId() != null
                            && !payment.getProviderRefundId().isBlank()
                            && !payment.getProviderRefundId().equals(result.refundId()))
                throw new IllegalStateException(
                        "PhonePe refund reference or amount did not match the recorded request.");
            return switch (result.state().trim().toUpperCase(java.util.Locale.ROOT)) {
                case "COMPLETED" ->
                        new RefundResult(PaymentStatus.REFUNDED, result.refundId(), null);
                case "FAILED" ->
                        new RefundResult(
                                PaymentStatus.REFUND_FAILED,
                                result.refundId(),
                                "PhonePe reported that the refund failed. Staff review is"
                                        + " required.");
                case "PENDING" ->
                        new RefundResult(PaymentStatus.REFUND_PENDING, result.refundId(), null);
                default ->
                        throw new IllegalStateException(
                                "PhonePe returned an unknown refund state.");
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePePaymentProvider.class,
                    "mapRefund(Payment,PhonePeClient.RefundResponse,boolean)");
        }
    }

    /**
     * Requires provider order id.
     *
     * @param payment the payment
     * @return the require provider order id result
     */
    private String requireProviderOrderId(Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePePaymentProvider.class, "requireProviderOrderId(Payment)");
        try {
            if (payment.getProviderOrderId() == null || payment.getProviderOrderId().isBlank()) {
                throw new IllegalStateException("PhonePe merchant order ID is missing.");
            }
            return payment.getProviderOrderId();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePePaymentProvider.class,
                    "requireProviderOrderId(Payment)");
        }
    }

    /**
     * Builds redirect url.
     *
     * @param orderNumber the order number
     * @return the build redirect url result
     */
    private String buildRedirectUrl(String orderNumber) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PhonePePaymentProvider.class, "buildRedirectUrl(String)");
        try {
            String baseUrl = properties.getRedirectUrl().replaceAll("/+$", "");
            return baseUrl + "/payment/" + URLEncoder.encode(orderNumber, StandardCharsets.UTF_8);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PhonePePaymentProvider.class,
                    "buildRedirectUrl(String)");
        }
    }
}
