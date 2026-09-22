package com.gokulsweets.restaurant.payment.dto;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        LocalDateTime expiresAt
) {
}
