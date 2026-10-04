package com.gokulsweets.restaurant.payment.provider.paytm;

public record PaytmRefundGatewayResult(

        String resultStatus,

        String resultCode,

        String resultMessage,

        String providerRefundId,

        java.math.BigDecimal refundAmount,

        String refId,

        String orderId,

        String txnId
) {
}
