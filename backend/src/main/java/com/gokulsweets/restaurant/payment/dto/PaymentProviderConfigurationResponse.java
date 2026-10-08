package com.gokulsweets.restaurant.payment.dto;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;

import java.util.Set;

/** Immutable payment provider configuration response data contract. */
public record PaymentProviderConfigurationResponse(
        PaymentProviderType defaultProvider, Set<PaymentProviderType> enabledProviders) {}
