package com.gokulsweets.restaurant.payment.provider.paytm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Immutable paytm initiate response data contract.
 *
 * @param head the head
 * @param body the body
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaytmInitiateResponse(Head head, Body body) {

    /**
     * Immutable head data contract.
     *
     * @param signature the signature
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Head(String signature) {}

    /**
     * Immutable body data contract.
     *
     * @param resultInfo the result info
     * @param txnToken the txn token
     * @param isPromoCodeValid the is promo code valid
     * @param authenticated the authenticated
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            ResultInfo resultInfo,
            String txnToken,
            Boolean isPromoCodeValid,
            Boolean authenticated) {}

    /**
     * Immutable result info data contract.
     *
     * @param resultStatus the result status
     * @param resultCode the result code
     * @param resultMsg the result msg
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultInfo(String resultStatus, String resultCode, String resultMsg) {}
}
