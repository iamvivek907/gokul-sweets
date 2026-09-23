package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class PaymentProviderRegistry {

    private final Map<PaymentProviderType, PaymentProvider> providers;
    private final Set<PaymentProviderType> enabledProviders;
    private final PaymentProviderType defaultProvider;

    public PaymentProviderRegistry(
            List<PaymentProvider> availableProviders,
            @Value("${payment.enabled-providers:RAZORPAY,PAYTM}")
            String enabled,
            @Value("${payment.default-provider:RAZORPAY}")
            String defaultProvider
    ) {
        EnumMap<PaymentProviderType, PaymentProvider> indexed =
                new EnumMap<>(PaymentProviderType.class);

        for (PaymentProvider provider : availableProviders) {
            PaymentProvider duplicate = indexed.put(
                    provider.providerType(),
                    provider
            );
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Multiple payment providers are registered for "
                                + provider.providerType()
                );
            }
        }

        this.providers = Map.copyOf(indexed);
        this.enabledProviders = parseEnabled(enabled);
        this.defaultProvider = parseType(defaultProvider);

        if (!enabledProviders.contains(this.defaultProvider)) {
            throw new IllegalStateException(
                    "The default payment provider must also be enabled."
            );
        }
        if (!providers.keySet().containsAll(enabledProviders)) {
            throw new IllegalStateException(
                    "One or more enabled payment providers are not registered."
            );
        }
    }

    public PaymentProvider require(
            PaymentProviderType requestedProvider
    ) {
        PaymentProviderType selected = requestedProvider == null
                ? defaultProvider
                : requestedProvider;

        if (!enabledProviders.contains(selected)) {
            throw new IllegalArgumentException(
                    "The selected payment provider is not enabled. Enabled providers: "
                            + enabledProviders
            );
        }

        PaymentProvider provider = providers.get(selected);
        if (provider == null) {
            throw new IllegalStateException(
                    "The selected payment provider is unavailable."
            );
        }
        return provider;
    }

    public PaymentProviderType defaultProvider() {
        return defaultProvider;
    }

    public Set<PaymentProviderType> enabledProviders() {
        return enabledProviders;
    }

    private Set<PaymentProviderType> parseEnabled(String value) {
        EnumSet<PaymentProviderType> parsed =
                EnumSet.noneOf(PaymentProviderType.class);

        for (String token : value.split(",")) {
            if (!token.isBlank()) {
                parsed.add(parseType(token));
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalStateException(
                    "At least one payment provider must be enabled."
            );
        }
        return Set.copyOf(parsed);
    }

    private PaymentProviderType parseType(String value) {
        try {
            return PaymentProviderType.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Unsupported payment provider configuration: " + value,
                    exception
            );
        }
    }
}
