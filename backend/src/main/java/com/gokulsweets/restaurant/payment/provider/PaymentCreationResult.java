package com.gokulsweets.restaurant.payment.provider;

/** Immutable payment creation result data contract. */
public record PaymentCreationResult(
        String providerPaymentId,
        String providerOrderId,
        String paymentSessionId,
        String paymentUrl,
        String checkoutKeyId) {

    public PaymentCreationResult(
            String providerPaymentId,
            String providerOrderId,
            String paymentSessionId,
            String paymentUrl) {
        this(providerPaymentId, providerOrderId, paymentSessionId, paymentUrl, null);
    }
}
