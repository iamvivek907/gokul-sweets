package com.gokulsweets.restaurant.payment.provider.paytm;

/**
 * Immutable paytm refund gateway result data contract.
 *
 * @param resultStatus the result status
 * @param resultCode the result code
 * @param resultMessage the result message
 * @param providerRefundId the provider refund id
 * @param refundAmount the refund amount
 * @param refId the ref id
 * @param orderId the order id
 * @param txnId the txn id
 */
public record PaytmRefundGatewayResult(
        String resultStatus,
        String resultCode,
        String resultMessage,
        String providerRefundId,
        java.math.BigDecimal refundAmount,
        String refId,
        String orderId,
        String txnId) {}
