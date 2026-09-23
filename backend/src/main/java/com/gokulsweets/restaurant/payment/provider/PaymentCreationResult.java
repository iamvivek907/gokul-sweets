package com.gokulsweets.restaurant.payment.provider;

public record PaymentCreationResult(
        String providerPaymentId,
        String providerOrderId,
        String paymentSessionId,
        String paymentUrl,
        String checkoutKeyId
) {

    public PaymentCreationResult(
            String providerPaymentId,
            String providerOrderId,
            String paymentSessionId,
            String paymentUrl
    ) {
        this(
                providerPaymentId,
                providerOrderId,
                paymentSessionId,
                paymentUrl,
                null
        );
    }
}