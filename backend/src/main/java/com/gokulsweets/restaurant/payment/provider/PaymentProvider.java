package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.payment.entity.Payment;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;

public interface PaymentProvider {

    PaymentProviderType providerType();

    PaymentCreationResult createPayment(
            Order order,
            Payment payment
    );

    PaymentVerificationResult verifyPayment(Payment payment);

    RefundResult refund(Payment payment);

    RefundResult verifyRefund(Payment payment);

    default String checkoutKeyId() {
        return null;
    }
}
