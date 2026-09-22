package com.gokulsweets.restaurant.payment.provider.paytm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaytmStatusResponse(
        Head head,
        Body body
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Head(
            String signature
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            ResultInfo resultInfo,
            String txnId,
            String bankTxnId,
            String orderId,
            String txnAmount,
            String txnType,
            String mid,
            String paymentMode
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultInfo(
            String resultStatus,
            String resultCode,
            String resultMsg
    ) {
    }
}