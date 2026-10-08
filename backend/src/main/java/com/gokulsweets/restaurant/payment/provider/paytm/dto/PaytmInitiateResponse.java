package com.gokulsweets.restaurant.payment.provider.paytm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Immutable paytm initiate response data contract. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaytmInitiateResponse(Head head, Body body) {

    /** Immutable head data contract. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Head(String signature) {}

    /** Immutable body data contract. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            ResultInfo resultInfo,
            String txnToken,
            Boolean isPromoCodeValid,
            Boolean authenticated) {}

    /** Immutable result info data contract. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultInfo(String resultStatus, String resultCode, String resultMsg) {}
}
