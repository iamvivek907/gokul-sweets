package com.gokulsweets.restaurant.payment.provider.paytm;

public record PaytmRefundGatewayResult(

        String resultStatus,

        String resultCode,

        String resultMessage,

        String providerRefundId
) {
}
