package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

/** Immutable payment verification result data contract. */
public record PaymentVerificationResult(
        PaymentStatus paymentStatus, String providerPaymentId, String failureReason) {}
