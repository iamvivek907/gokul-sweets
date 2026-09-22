package com.gokulsweets.restaurant.payment.provider.paytm;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaytmPaymentProvider implements PaymentProvider {

    private final PaytmClient paytmClient;

    @Override
    public PaymentProviderType providerType() {
        return PaymentProviderType.PAYTM;
    }

    @Override
    public PaymentCreationResult createPayment(
            Order order,
            Payment payment
    ) {
        String providerOrderId = "GKS-PAY-" + payment.getId();
        String customerId = "GKS-CUST-" + order.getId();

        PaytmInitiateResponse response = paytmClient.initiateTransaction(
                providerOrderId,
                payment.getAmount(),
                customerId
        );

        if (response.body() == null
                || response.body().resultInfo() == null) {
            throw new IllegalStateException(
                    "Invalid response received from Paytm."
            );
        }
        if (!"S".equalsIgnoreCase(
                response.body().resultInfo().resultStatus()
        )) {
            log.warn(
                    "Paytm initiation rejected: paymentId={}, code={}, status={}",
                    payment.getId(),
                    response.body().resultInfo().resultCode(),
                    response.body().resultInfo().resultStatus()
            );
            throw new IllegalStateException(
                    "Paytm could not initiate the payment."
            );
        }

        String token = response.body().txnToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Paytm did not return a transaction token."
            );
        }

        return new PaymentCreationResult(
                null,
                providerOrderId,
                token,
                null,
                null
        );
    }

    @Override
    public PaymentVerificationResult verifyPayment(Payment payment) {
        String providerOrderId = requireProviderOrderId(payment);
        PaytmStatusResponse response = paytmClient.getTransactionStatus(
                providerOrderId
        );

        if (response.body() == null
                || response.body().resultInfo() == null) {
            throw new IllegalStateException(
                    "Invalid transaction status received from Paytm."
            );
        }

        return switch (response.body().resultInfo().resultStatus()) {
            case "TXN_SUCCESS" -> new PaymentVerificationResult(
                    PaymentStatus.PAID,
                    response.body().txnId(),
                    null
            );
            case "TXN_FAILURE" -> new PaymentVerificationResult(
                    PaymentStatus.FAILED,
                    response.body().txnId(),
                    response.body().resultInfo().resultMsg()
            );
            default -> new PaymentVerificationResult(
                    PaymentStatus.PENDING,
                    response.body().txnId(),
                    null
            );
        };
    }

    @Override
    public RefundResult refund(Payment payment) {
        validateRefundable(payment);
        return mapRefund(
                paytmClient.initiateRefund(
                        requireProviderOrderId(payment),
                        payment.getProviderPaymentId(),
                        payment.getRefundReferenceId(),
                        payment.getAmount()
                ),
                true
        );
    }

    @Override
    public RefundResult verifyRefund(Payment payment) {
        if (payment.getRefundReferenceId() == null
                || payment.getRefundReferenceId().isBlank()) {
            throw new IllegalStateException(
                    "Refund reference ID is missing."
            );
        }
        return mapRefund(
                paytmClient.getRefundStatus(
                        requireProviderOrderId(payment),
                        payment.getRefundReferenceId()
                ),
                false
        );
    }

    private RefundResult mapRefund(
            PaytmRefundGatewayResult response,
            boolean initiation
    ) {
        String status = response.resultStatus();
        String code = response.resultCode();

        if ("TXN_SUCCESS".equalsIgnoreCase(status)
                || "629".equals(code)) {
            return new RefundResult(
                    PaymentStatus.REFUNDED,
                    response.providerRefundId(),
                    null
            );
        }
        if ("PENDING".equalsIgnoreCase(status)
                || "NO_RECORD_FOUND".equalsIgnoreCase(status)
                || "617".equals(code)
                || "626".equals(code)
                || "628".equals(code)
                || "677".equals(code)) {
            return new RefundResult(
                    PaymentStatus.REFUND_PENDING,
                    response.providerRefundId(),
                    null
            );
        }

        return new RefundResult(
                PaymentStatus.REFUND_FAILED,
                response.providerRefundId(),
                response.resultMessage() != null
                        ? response.resultMessage()
                        : initiation
                        ? "Paytm refund initiation failed."
                        : "Paytm refund failed."
        );
    }

    private String requireProviderOrderId(Payment payment) {
        if (payment.getProviderOrderId() == null
                || payment.getProviderOrderId().isBlank()) {
            throw new IllegalStateException(
                    "Paytm order ID is missing."
            );
        }
        return payment.getProviderOrderId();
    }

    private void validateRefundable(Payment payment) {
        requireProviderOrderId(payment);
        if (payment.getProviderPaymentId() == null
                || payment.getProviderPaymentId().isBlank()
                || payment.getRefundReferenceId() == null
                || payment.getRefundReferenceId().isBlank()
                || payment.getAmount() == null) {
            throw new IllegalStateException(
                    "Complete Paytm refund information is required."
            );
        }
    }
}
