package com.gokulsweets.restaurant.payment.provider;

/**
 * Immutable payment creation result data contract.
 *
 * @param providerPaymentId the provider payment id
 * @param providerOrderId the provider order id
 * @param paymentSessionId the payment session id
 * @param paymentUrl the payment url
 * @param checkoutKeyId the checkout key id
 */
public record PaymentCreationResult(
        String providerPaymentId,
        String providerOrderId,
        String paymentSessionId,
        String paymentUrl,
        String checkoutKeyId) {

    /**
     * Creates a payment creation result instance.
     *
     * @param providerPaymentId the provider payment id
     * @param providerOrderId the provider order id
     * @param paymentSessionId the payment session id
     * @param paymentUrl the payment url
     */
    public PaymentCreationResult(
            String providerPaymentId,
            String providerOrderId,
            String paymentSessionId,
            String paymentUrl) {
        this(providerPaymentId, providerOrderId, paymentSessionId, paymentUrl, null);
    }
}
