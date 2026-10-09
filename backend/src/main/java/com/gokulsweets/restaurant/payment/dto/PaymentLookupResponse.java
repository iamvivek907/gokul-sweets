package com.gokulsweets.restaurant.payment.dto;

/**
 * Immutable payment lookup response data contract.
 *
 * @param payment the payment
 */
public record PaymentLookupResponse(PaymentResponse payment) {}
