package com.gokulsweets.restaurant.payment.provider.paytm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Immutable paytm status response data contract. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaytmStatusResponse(Head head, Body body) {

    /** Immutable head data contract. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Head(String signature) {}

    /** Immutable body data contract. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            ResultInfo resultInfo,
            String txnId,
            String bankTxnId,
            String orderId,
            String txnAmount,
            String txnType,
            String mid,
            String paymentMode) {}

    /** Immutable result info data contract. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultInfo(String resultStatus, String resultCode, String resultMsg) {}
}
