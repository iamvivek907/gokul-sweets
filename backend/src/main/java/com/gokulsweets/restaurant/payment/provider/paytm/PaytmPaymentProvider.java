package com.gokulsweets.restaurant.payment.provider.paytm;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;
import com.gokulsweets.restaurant.payment.provider.PaymentCreationResult;
import com.gokulsweets.restaurant.payment.provider.PaymentProvider;
import com.gokulsweets.restaurant.payment.provider.PaymentVerificationResult;
import com.gokulsweets.restaurant.payment.provider.RefundResult;
import com.gokulsweets.restaurant.payment.provider.paytm.dto.PaytmInitiateResponse;
import com.gokulsweets.restaurant.payment.provider.paytm.dto.PaytmStatusResponse;
import com.gokulsweets.restaurant.payment.service.PaymentReconciliationPolicy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/** Backend paytm payment provider contract and implementation. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaytmPaymentProvider implements PaymentProvider {

    private final PaytmClient paytmClient;

    private final PaymentReconciliationPolicy reconciliationPolicy;

    /**
     * Providers type.
     *
     * @return the provider type result
     */
    @Override
    public PaymentProviderType providerType() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaytmPaymentProvider.class, "providerType()");
        try {
            return PaymentProviderType.PAYTM;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaytmPaymentProvider.class, "providerType()");
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
                MethodTiming.start(PaytmPaymentProvider.class, "createPayment(Order,Payment)");
        try {
            String providerOrderId = "GKS-PAY-" + payment.getId();
            String customerId = "GKS-CUST-" + order.getId();
            PaytmInitiateResponse response =
                    paytmClient.initiateTransaction(
                            providerOrderId, payment.getAmount(), customerId);
            if (response.body() == null || response.body().resultInfo() == null) {
                throw new IllegalStateException("Invalid response received from Paytm.");
            }
            if (!"S".equalsIgnoreCase(response.body().resultInfo().resultStatus())) {
                log.warn(
                        "Paytm initiation rejected: paymentId={}, code={}, status={}",
                        payment.getId(),
                        response.body().resultInfo().resultCode(),
                        response.body().resultInfo().resultStatus());
                throw new IllegalStateException("Paytm could not initiate the payment.");
            }
            String token = response.body().txnToken();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Paytm did not return a transaction token.");
            }
            return new PaymentCreationResult(null, providerOrderId, token, null, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaytmPaymentProvider.class,
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
                MethodTiming.start(PaytmPaymentProvider.class, "verifyPayment(Payment)");
        try {
            String providerOrderId = requireProviderOrderId(payment);
            PaytmStatusResponse response = paytmClient.getTransactionStatus(providerOrderId);
            if (response.body() == null || response.body().resultInfo() == null) {
                throw new IllegalStateException("Invalid transaction status received from Paytm.");
            }
            if ("TXN_SUCCESS".equals(response.body().resultInfo().resultStatus())) {
                reconciliationPolicy.validatePaytmStatus(payment, response.body());
            }
            return switch (response.body().resultInfo().resultStatus()) {
                case "TXN_SUCCESS" ->
                        new PaymentVerificationResult(
                                PaymentStatus.PAID, response.body().txnId(), null);
                case "TXN_FAILURE" ->
                        new PaymentVerificationResult(
                                PaymentStatus.FAILED,
                                response.body().txnId(),
                                response.body().resultInfo().resultMsg());
                default ->
                        new PaymentVerificationResult(
                                PaymentStatus.PENDING, response.body().txnId(), null);
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaytmPaymentProvider.class,
                    "verifyPayment(Payment)");
        }
    }

    /**
     * Refunds paytm payment provider data and returns the {@code RefundResult} result.
     *
     * @param payment the payment supplied to this method
     * @return the value of {@code mapRefund(payment,
     *     paytmClient.initiateRefund(requireProviderOrderId(payment),
     *     payment.getProviderPaymentId(), payment.getRefundReferenceId(),
     *     payment.requestedRefundAmount()), true)}
     */
    @Override
    public RefundResult refund(Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaytmPaymentProvider.class, "refund(Payment)");
        try {
            validateRefundable(payment);
            return mapRefund(
                    payment,
                    paytmClient.initiateRefund(
                            requireProviderOrderId(payment),
                            payment.getProviderPaymentId(),
                            payment.getRefundReferenceId(),
                            payment.requestedRefundAmount()),
                    true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaytmPaymentProvider.class, "refund(Payment)");
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
                MethodTiming.start(PaytmPaymentProvider.class, "verifyRefund(Payment)");
        try {
            if (payment.getRefundReferenceId() == null
                    || payment.getRefundReferenceId().isBlank()) {
                throw new IllegalStateException("Refund reference ID is missing.");
            }
            return mapRefund(
                    payment,
                    paytmClient.getRefundStatus(
                            requireProviderOrderId(payment), payment.getRefundReferenceId()),
                    false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaytmPaymentProvider.class, "verifyRefund(Payment)");
        }
    }

    /**
     * Maps refund.
     *
     * @param payment the payment
     * @param response the response
     * @param initiation the initiation
     * @return the map refund result
     */
    private RefundResult mapRefund(
            Payment payment, PaytmRefundGatewayResult response, boolean initiation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaytmPaymentProvider.class,
                        "mapRefund(Payment,PaytmRefundGatewayResult,boolean)");
        try {
            validateRefundResponse(payment, response);
            String status = response.resultStatus();
            String code = response.resultCode();
            if ("TXN_SUCCESS".equalsIgnoreCase(status) || "629".equals(code)) {
                return new RefundResult(PaymentStatus.REFUNDED, response.providerRefundId(), null);
            }
            if ("PENDING".equalsIgnoreCase(status)
                    || "NO_RECORD_FOUND".equalsIgnoreCase(status)
                    || "617".equals(code)
                    || "626".equals(code)
                    || "628".equals(code)
                    || "677".equals(code)) {
                return new RefundResult(
                        PaymentStatus.REFUND_PENDING, response.providerRefundId(), null);
            }
            return new RefundResult(
                    PaymentStatus.REFUND_FAILED,
                    response.providerRefundId(),
                    response.resultMessage() != null
                            ? response.resultMessage()
                            : initiation
                                    ? "Paytm refund initiation failed."
                                    : "Paytm refund failed.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaytmPaymentProvider.class,
                    "mapRefund(Payment,PaytmRefundGatewayResult,boolean)");
        }
    }

    /**
     * Validates refund response.
     *
     * @param payment the payment
     * @param response the response
     */
    private void validateRefundResponse(Payment payment, PaytmRefundGatewayResult response) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaytmPaymentProvider.class,
                        "validateRefundResponse(Payment,PaytmRefundGatewayResult)");
        try {
            if (!requireProviderOrderId(payment).equals(response.orderId())
                    || payment.getProviderPaymentId() == null
                    || !payment.getProviderPaymentId().equals(response.txnId())
                    || payment.getRefundReferenceId() == null
                    || !payment.getRefundReferenceId().equals(response.refId())
                    || response.refundAmount() == null
                    || payment.requestedRefundAmount().compareTo(response.refundAmount()) != 0
                    || response.providerRefundId() == null
                    || response.providerRefundId().isBlank()
                    || payment.getProviderRefundId() != null
                            && !payment.getProviderRefundId().isBlank()
                            && !payment.getProviderRefundId().equals(response.providerRefundId())) {
                throw new IllegalStateException(
                        "Paytm refund amount or identifiers do not match the recorded refund.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaytmPaymentProvider.class,
                    "validateRefundResponse(Payment,PaytmRefundGatewayResult)");
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
                MethodTiming.start(PaytmPaymentProvider.class, "requireProviderOrderId(Payment)");
        try {
            if (payment.getProviderOrderId() == null || payment.getProviderOrderId().isBlank()) {
                throw new IllegalStateException("Paytm order ID is missing.");
            }
            return payment.getProviderOrderId();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaytmPaymentProvider.class,
                    "requireProviderOrderId(Payment)");
        }
    }

    /**
     * Validates refundable.
     *
     * @param payment the payment
     */
    private void validateRefundable(Payment payment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaytmPaymentProvider.class, "validateRefundable(Payment)");
        try {
            requireProviderOrderId(payment);
            if (payment.getProviderPaymentId() == null
                    || payment.getProviderPaymentId().isBlank()
                    || payment.getRefundReferenceId() == null
                    || payment.getRefundReferenceId().isBlank()
                    || payment.getAmount() == null) {
                throw new IllegalStateException("Complete Paytm refund information is required.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaytmPaymentProvider.class,
                    "validateRefundable(Payment)");
        }
    }
}
