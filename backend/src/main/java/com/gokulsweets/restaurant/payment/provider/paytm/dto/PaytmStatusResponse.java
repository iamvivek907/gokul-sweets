package com.gokulsweets.restaurant.payment.provider.paytm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Immutable paytm status response data contract.
 *
 * @param head the head
 * @param body the body
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaytmStatusResponse(Head head, Body body) {

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
     * @param txnId the txn id
     * @param bankTxnId the bank txn id
     * @param orderId the order id
     * @param txnAmount the txn amount
     * @param txnType the txn type
     * @param mid the mid
     * @param paymentMode the payment mode
     */
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
