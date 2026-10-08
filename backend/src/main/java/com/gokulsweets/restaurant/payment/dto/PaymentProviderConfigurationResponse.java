package com.gokulsweets.restaurant.payment.dto;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;

import java.util.Set;

/**
 * Immutable payment provider configuration response data contract.
 *
 * @param defaultProvider the default provider
 * @param enabledProviders the enabled providers
 */
public record PaymentProviderConfigurationResponse(
        PaymentProviderType defaultProvider, Set<PaymentProviderType> enabledProviders) {}
