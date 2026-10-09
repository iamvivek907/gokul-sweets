package com.gokulsweets.restaurant.order.lifecycle.config;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Backend pickup lifecycle properties contract and implementation. */
@Component
@ConfigurationProperties(prefix = "order.pickup-lifecycle")
@Validated
@Getter
@Setter
public class PickupLifecycleProperties {

    private boolean automaticExpiryEnabled = false;

    @Min(0)
    @Max(1440)
    private int pickupExpiryGraceMinutes = 30;

    @Min(1)
    @Max(10080)
    private int noShowAfterMinutes = 240;

    @Min(1)
    @Max(1000)
    private int batchSize = 100;

    /**
     * Reports whether no show after expiry.
     *
     * @return the is no show after expiry result
     */
    @AssertTrue(message = "No-show time must be later than pickup-expiry grace time.")
    public boolean isNoShowAfterExpiry() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PickupLifecycleProperties.class, "isNoShowAfterExpiry()");
        try {
            return noShowAfterMinutes > pickupExpiryGraceMinutes;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PickupLifecycleProperties.class,
                    "isNoShowAfterExpiry()");
        }
    }
}
