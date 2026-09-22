package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.payment.enums.PaymentStatus;

public record RefundResult(

        PaymentStatus paymentStatus,

        String providerRefundId,

        String failureReason
) {
}
