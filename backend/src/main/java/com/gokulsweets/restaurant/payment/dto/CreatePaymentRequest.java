package com.gokulsweets.restaurant.payment.dto;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(
        @NotBlank(message = "Order number is required.")
        String orderNumber,

        /* Null deliberately selects payment.default-provider. */
        PaymentProviderType provider
) {
}
