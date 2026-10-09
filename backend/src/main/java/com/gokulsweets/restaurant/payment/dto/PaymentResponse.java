package com.gokulsweets.restaurant.payment.dto;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable payment response data contract.
 *
 * @param paymentId the payment id
 * @param orderNumber the order number
 * @param provider the provider
 * @param paymentStatus the payment status
 * @param amount the amount
 * @param currency the currency
 * @param providerPaymentId the provider payment id
 * @param providerOrderId the provider order id
 * @param paymentSessionId the payment session id
 * @param paymentUrl the payment url
 * @param checkoutKeyId the checkout key id
 * @param expiresAt the expires at
 */
public record PaymentResponse(
        Long paymentId,
        String orderNumber,
        PaymentProviderType provider,
        PaymentStatus paymentStatus,
        BigDecimal amount,
        String currency,
        String providerPaymentId,
        String providerOrderId,
        String paymentSessionId,
        String paymentUrl,
        String checkoutKeyId,
        LocalDateTime expiresAt) {}
