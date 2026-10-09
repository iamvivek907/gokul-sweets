package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

/**
 * Immutable refund result data contract.
 *
 * @param paymentStatus the payment status
 * @param providerRefundId the provider refund id
 * @param failureReason the failure reason
 */
public record RefundResult(
        PaymentStatus paymentStatus, String providerRefundId, String failureReason) {}
