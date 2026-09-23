package com.gokulsweets.restaurant.payment.provider.phonepe;

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
public class PhonePePaymentProvider implements PaymentProvider {

    private final PhonePeClient client;
    private final PhonePeProperties properties;

    @Override
    public PaymentProviderType providerType() {
        return PaymentProviderType.PHONEPE;
    }

    @Override
    public PaymentCreationResult createPayment(
            Order order,
            Payment payment
    ) {
        properties.requireApiConfiguration();
        String merchantTransactionId = "GKS-PPE-" + payment.getId();
        String redirectUrl = properties.getRedirectUrl();
        if (redirectUrl == null || redirectUrl.isBlank()) {
            throw new IllegalStateException(
                    "PhonePe redirect URL is not configured."
            );
        }

        PhonePeClient.CreatePaymentResponse created = client.createPayment(
                merchantTransactionId,
                payment.getAmount(),
                redirectUrl,
                properties.getCallbackUrl()
        );

        if (!created.success() || created.redirectUrl() == null || created.redirectUrl().isBlank()) {
            throw new IllegalStateException(
                    created.message() == null || created.message().isBlank()
                            ? "PhonePe could not initiate the payment."
                            : created.message()
            );
        }

        return new PaymentCreationResult(
                created.transactionId(),
                merchantTransactionId,
                null,
                created.redirectUrl()
        );
    }

    @Override
    public PaymentVerificationResult verifyPayment(Payment payment) {
        String providerOrderId = requireProviderOrderId(payment);
        PhonePeClient.StatusResponse status = client.checkStatus(providerOrderId);

        String state = status.state() == null
                ? "PENDING"
                : status.state().trim().toUpperCase();

        return switch (state) {
            case "COMPLETED" -> new PaymentVerificationResult(
                    PaymentStatus.PAID,
                    status.transactionId(),
                    null
            );
            case "FAILED" -> new PaymentVerificationResult(
                    PaymentStatus.FAILED,
                    status.transactionId(),
                    failureMessage(status)
            );
            default -> new PaymentVerificationResult(
                    PaymentStatus.PENDING,
                    status.transactionId(),
                    null
            );
        };
    }

    @Override
    public RefundResult refund(Payment payment) {
        return new RefundResult(
                PaymentStatus.REFUND_FAILED,
                null,
                "Automatic PhonePe refunds are not configured. Manual refund is required."
        );
    }

    @Override
    public RefundResult verifyRefund(Payment payment) {
        return new RefundResult(
                PaymentStatus.REFUND_FAILED,
                null,
                "Automatic PhonePe refund status checks are not configured."
        );
    }

    private String requireProviderOrderId(Payment payment) {
        if (payment.getProviderOrderId() == null
                || payment.getProviderOrderId().isBlank()) {
            throw new IllegalStateException(
                    "PhonePe merchant transaction ID is missing."
            );
        }
        return payment.getProviderOrderId();
    }

    private String failureMessage(PhonePeClient.StatusResponse status) {
        if (status.message() != null && !status.message().isBlank()) {
            return status.message();
        }
        if (status.responseCode() != null && !status.responseCode().isBlank()) {
            return "PhonePe declined this payment (" + status.responseCode() + ").";
        }
        return "PhonePe reported that the payment failed.";
    }
}
