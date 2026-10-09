package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Backend order number generator contract and implementation. */
@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final Clock clock;

    private final EnhancementProperties features;

    /**
     * Creates a order number generator instance.
     *
     * @param clock the clock
     * @param features the features
     */
    public OrderNumberGenerator(Clock clock, EnhancementProperties features) {
        this.clock = clock;
        this.features = features;
    }

    /**
     * Generates order number generator data and returns the {@code String} result.
     *
     * @return the {@code String} result
     */
    public String generate() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OrderNumberGenerator.class, "generate()");
        try {
            String date =
                    LocalDate.now(
                                    features.isIstTimeFixEnabled()
                                            ? clock.withZone(ApplicationClock.BUSINESS_ZONE)
                                            : clock.withZone(java.time.ZoneId.systemDefault()))
                            .format(DATE_FORMAT);
            String randomPart =
                    UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            return "GKS-" + date + "-" + randomPart;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OrderNumberGenerator.class, "generate()");
        }
    }
}
