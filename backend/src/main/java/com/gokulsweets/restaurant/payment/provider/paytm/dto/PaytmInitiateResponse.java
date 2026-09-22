package com.gokulsweets.restaurant.payment.provider.paytm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaytmInitiateResponse(
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
            String txnToken,
            Boolean isPromoCodeValid,
            Boolean authenticated
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