package com.gokulsweets.restaurant.payment.provider;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Backend payment provider registry contract and implementation. */
@Component
public class PaymentProviderRegistry {

    private final Map<PaymentProviderType, PaymentProvider> providers;

    private final Set<PaymentProviderType> enabledProviders;

    private final PaymentProviderType defaultProvider;

    /**
     * Creates a payment provider registry instance.
     *
     * @param availableProviders the available providers
     * @param enabled the enabled
     * @param defaultProvider the default provider
     */
    public PaymentProviderRegistry(
            List<PaymentProvider> availableProviders,
            @Value("${payment.enabled-providers:RAZORPAY,PAYTM}") String enabled,
            @Value("${payment.default-provider:RAZORPAY}") String defaultProvider) {
        EnumMap<PaymentProviderType, PaymentProvider> indexed =
                new EnumMap<>(PaymentProviderType.class);
        for (PaymentProvider provider : availableProviders) {
            PaymentProvider duplicate = indexed.put(provider.providerType(), provider);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Multiple payment providers are registered for " + provider.providerType());
            }
        }
        this.providers = Map.copyOf(indexed);
        this.enabledProviders = parseEnabled(enabled);
        this.defaultProvider = parseType(defaultProvider);
        if (!enabledProviders.contains(this.defaultProvider)) {
            throw new IllegalStateException("The default payment provider must also be enabled.");
        }
        if (!providers.keySet().containsAll(enabledProviders)) {
            throw new IllegalStateException(
                    "One or more enabled payment providers are not registered.");
        }
    }

    /**
     * Requires payment provider registry data and returns the {@code PaymentProvider} result.
     *
     * @param requestedProvider the requested provider supplied to this method
     * @return the value of {@code provider}
     * @throws IllegalStateException when the method rejects the request with {@code The selected
     *     payment provider is unavailable.}
     */
    public PaymentProvider require(PaymentProviderType requestedProvider) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentProviderRegistry.class, "require(PaymentProviderType)");
        try {
            PaymentProviderType selected =
                    requestedProvider == null ? defaultProvider : requestedProvider;
            if (!enabledProviders.contains(selected)) {
                throw new IllegalArgumentException(
                        "The selected payment provider is not enabled. Enabled providers: "
                                + enabledProviders);
            }
            PaymentProvider provider = providers.get(selected);
            if (provider == null) {
                throw new IllegalStateException("The selected payment provider is unavailable.");
            }
            return provider;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentProviderRegistry.class,
                    "require(PaymentProviderType)");
        }
    }

    /**
     * Defaults provider.
     *
     * @return the default provider result
     */
    public PaymentProviderType defaultProvider() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentProviderRegistry.class, "defaultProvider()");
        try {
            return defaultProvider;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentProviderRegistry.class, "defaultProvider()");
        }
    }

    /**
     * Enableds providers.
     *
     * @return the enabled providers result
     */
    public Set<PaymentProviderType> enabledProviders() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentProviderRegistry.class, "enabledProviders()");
        try {
            return enabledProviders;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentProviderRegistry.class, "enabledProviders()");
        }
    }

    /**
     * Parses enabled.
     *
     * @param value the value
     * @return the parse enabled result
     */
    private Set<PaymentProviderType> parseEnabled(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentProviderRegistry.class, "parseEnabled(String)");
        try {
            EnumSet<PaymentProviderType> parsed = EnumSet.noneOf(PaymentProviderType.class);
            for (String token : value.split(",")) {
                if (!token.isBlank()) {
                    parsed.add(parseType(token));
                }
            }
            if (parsed.isEmpty()) {
                throw new IllegalStateException("At least one payment provider must be enabled.");
            }
            return Set.copyOf(parsed);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentProviderRegistry.class,
                    "parseEnabled(String)");
        }
    }

    /**
     * Parses type.
     *
     * @param value the value
     * @return the parse type result
     */
    private PaymentProviderType parseType(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentProviderRegistry.class, "parseType(String)");
        try {
            try {
                return PaymentProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "Unsupported payment provider configuration: " + value, exception);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentProviderRegistry.class, "parseType(String)");
        }
    }
}
