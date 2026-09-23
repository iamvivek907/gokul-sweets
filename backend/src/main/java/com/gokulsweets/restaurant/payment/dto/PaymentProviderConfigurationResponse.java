package com.gokulsweets.restaurant.payment.dto;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;

import java.util.Set;

public record PaymentProviderConfigurationResponse(
        PaymentProviderType defaultProvider,
        Set<PaymentProviderType> enabledProviders
) {
}
