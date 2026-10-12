package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

/**
 * Immutable payment verification result data contract.
 *
 * @param paymentStatus the payment status
 * @param providerPaymentId the provider payment id
 * @param failureReason the failure reason
 */
public record PaymentVerificationResult(
        PaymentStatus paymentStatus, String providerPaymentId, String failureReason) {}
