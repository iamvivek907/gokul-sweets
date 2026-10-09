package com.gokulsweets.restaurant.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Initializes method diagnostics from operator-controlled properties without adding bean proxies.
 */
@Component
public final class MethodTimingConfiguration {

    /**
     * Applies timing settings once the Spring application creates this component.
     *
     * @param enabled whether method elapsed-time diagnostics are active
     * @param slowThresholdMillis inclusive elapsed duration that triggers a slow-method warning
     */
    public MethodTimingConfiguration(
            @Value("${gokul.method-timing.enabled:true}") boolean enabled,
            @Value("${gokul.method-timing.slow-threshold-ms:1000}") long slowThresholdMillis) {
        MethodTiming.configure(enabled, slowThresholdMillis);
    }
}
