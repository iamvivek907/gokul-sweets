package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

public record PaymentVerificationResult(

        PaymentStatus paymentStatus,

        String providerPaymentId,

        String failureReason
) {
}