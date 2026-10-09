package com.gokulsweets.restaurant.payment.dto;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;

import jakarta.validation.constraints.NotBlank;

/**
 * Immutable create payment request data contract.
 *
 * @param orderNumber the order number
 * @param provider the provider
 */
public record CreatePaymentRequest(
        @NotBlank(message = "Order number is required.")
                String orderNumber, /* Null deliberately selects payment.default-provider. */
        PaymentProviderType provider) {}
