package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

/** Immutable refund result data contract. */
public record RefundResult(
        PaymentStatus paymentStatus, String providerRefundId, String failureReason) {}
