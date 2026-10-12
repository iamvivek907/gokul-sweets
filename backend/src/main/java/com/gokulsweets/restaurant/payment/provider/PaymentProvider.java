package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;

/** Backend payment provider contract and implementation. */
public interface PaymentProvider {

    /**
     * Providers type.
     *
     * @return the provider type result
     */
    PaymentProviderType providerType();

    /**
     * Creates payment.
     *
     * @param order the order
     * @param payment the payment
     * @return the create payment result
     */
    PaymentCreationResult createPayment(Order order, Payment payment);

    /**
     * Verify payment.
     *
     * @param payment the payment
     * @return the verify payment result
     */
    PaymentVerificationResult verifyPayment(Payment payment);

    /**
     * Refunds payment provider data and returns the {@code RefundResult} result.
     *
     * @param payment the payment supplied to this method
     * @return the {@code RefundResult} result
     */
    RefundResult refund(Payment payment);

    /**
     * Verify refund.
     *
     * @param payment the payment
     * @return the verify refund result
     */
    RefundResult verifyRefund(Payment payment);

    /**
     * Checkouts key id.
     *
     * @return the checkout key id result
     */
    default String checkoutKeyId() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentProvider.class, "checkoutKeyId()");
        try {
            return null;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentProvider.class, "checkoutKeyId()");
        }
    }
}
