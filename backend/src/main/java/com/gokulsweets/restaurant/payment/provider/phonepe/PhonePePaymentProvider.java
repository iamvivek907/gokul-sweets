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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        properties.requireRedirectConfiguration();

        /*
         * This is our own merchant-side identifier.
         *
         * It is stored in Payment.providerOrderId and must be used
         * for all subsequent PhonePe status checks.
         */
        String merchantOrderId =
                "GKS-PPE-" + payment.getId();

        /*
         * PhonePe redirects the customer back to our payment page.
         *
         * Example:
         * https://gokul-sweets-dev.vercel.app/checkout/payment/GKS-20260923-490EAC17BE04497A
         */
        String redirectUrl =
                buildRedirectUrl(order.getOrderNumber());

        PhonePeClient.CreatePaymentResponse created =
                client.createPayment(
                        merchantOrderId,
                        payment.getAmount(),
                        redirectUrl
                );

        if (created.orderId() == null
                || created.orderId().isBlank()) {

            throw new IllegalStateException(
                    created.message() == null
                            || created.message().isBlank()
                            ? "PhonePe did not return a PhonePe order ID."
                            : created.message()
            );
        }

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
                client.checkStatus(merchantOrderId);

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
                                    || status.errorMessage().isBlank()
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
        String baseUrl =
                properties.getRedirectUrl()
                        .replaceAll("/+$", "");

        return baseUrl
                + "/checkout/payment/"
                + URLEncoder.encode(
                orderNumber,
                StandardCharsets.UTF_8
        );
    }
}