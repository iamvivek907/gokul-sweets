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

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class PhonePePaymentProvider
        implements PaymentProvider {

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
        properties.requireRedirectConfiguration();

        /*
         * This ID is persisted as providerOrderId.
         *
         * It must be:
         * - unique
         * - <= 63 characters
         * - only letters/numbers/_/-
         */
        String merchantOrderId =
                "GKS-PPE-" + payment.getId();

        String redirectUrl =
                buildRedirectUrl(
                        order.getOrderNumber()
                );

        PhonePeClient.CreatePaymentResponse created =
                client.createPayment(
                        merchantOrderId,
                        payment.getAmount(),
                        redirectUrl
                );

        if (created.redirectUrl() == null
                || created.redirectUrl().isBlank()) {

            throw new IllegalStateException(
                    created.message() == null
                            || created.message().isBlank()
                            ? "PhonePe did not return a checkout URL."
                            : created.message()
            );
        }

        /*
         * Mapping:
         *
         * providerPaymentId = PhonePe orderId
         * providerOrderId   = our merchantOrderId
         * paymentSessionId  = null
         * paymentUrl        = PhonePe redirectUrl
         * checkoutKeyId     = null
         */
        return new PaymentCreationResult(
                created.orderId(),
                created.merchantOrderId(),
                null,
                created.redirectUrl(),
                null
        );
    }

    @Override
    public PaymentVerificationResult verifyPayment(
            Payment payment
    ) {
        String merchantOrderId =
                requireProviderOrderId(payment);

        PhonePeClient.StatusResponse status =
                client.checkStatus(
                        merchantOrderId
                );

        String state =
                status.state() == null
                        ? "PENDING"
                        : status.state()
                        .trim()
                        .toUpperCase();

        return switch (state) {

            case "COMPLETED" ->
                    new PaymentVerificationResult(
                            PaymentStatus.PAID,
                            status.transactionId(),
                            null
                    );

            case "FAILED" ->
                    new PaymentVerificationResult(
                            PaymentStatus.FAILED,
                            status.transactionId(),
                            status.errorMessage() == null
                                    ? "PhonePe reported that the payment failed."
                                    : status.errorMessage()
                    );

            default ->
                    new PaymentVerificationResult(
                            PaymentStatus.PENDING,
                            status.transactionId(),
                            null
                    );
        };
    }

    @Override
    public RefundResult refund(
            Payment payment
    ) {
        /*
         * We are deliberately not implementing automatic refunds
         * in this first V2 migration.
         *
         * Your existing PaymentStatusService will continue to
         * represent the refund lifecycle correctly.
         */
        return new RefundResult(
                PaymentStatus.REFUND_FAILED,
                null,
                "Automatic PhonePe refunds are not configured. Manual refund is required."
        );
    }

    @Override
    public RefundResult verifyRefund(
            Payment payment
    ) {
        return new RefundResult(
                PaymentStatus.REFUND_FAILED,
                null,
                "Automatic PhonePe refund status checks are not configured."
        );
    }

    private String requireProviderOrderId(
            Payment payment
    ) {
        if (payment.getProviderOrderId() == null
                || payment.getProviderOrderId().isBlank()) {

            throw new IllegalStateException(
                    "PhonePe merchant order ID is missing."
            );
        }

        return payment.getProviderOrderId();
    }

    private String buildRedirectUrl(
            String orderNumber
    ) {
        return properties
                .getRedirectUrl()
                .replaceAll("/+$", "")
                + "/payment/"
                + java.net.URLEncoder.encode(
                orderNumber,
                java.nio.charset.StandardCharsets.UTF_8
        );
    }
}