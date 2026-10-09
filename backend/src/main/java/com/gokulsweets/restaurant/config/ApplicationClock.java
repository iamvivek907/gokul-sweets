package com.gokulsweets.restaurant.config;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Backend application clock contract and implementation. */
@Component
public class ApplicationClock {

    /** Local business dates and legacy database timestamps are interpreted in this zone. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private static volatile boolean istTimeFixEnabled = true;

    /**
     * Creates a application clock instance.
     *
     * @param features the features
     */
    public ApplicationClock(EnhancementProperties features) {
        istTimeFixEnabled = features.isIstTimeFixEnabled();
    }

    /**
     * Entity callbacks cannot inject a Clock. This rollout switch makes their legacy LocalDateTime
     * timestamps consistent with payment and slot logic. Switch off only while investigating an
     * existing database timestamp migration.
     *
     * @return the operation result
     */
    public static LocalDateTime legacyTimestampNow() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ApplicationClock.class, "legacyTimestampNow()");
        try {
            return istTimeFixEnabled ? LocalDateTime.now(BUSINESS_ZONE) : LocalDateTime.now();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, ApplicationClock.class, "legacyTimestampNow()");
        }
    }

    /**
     * Returns zone information for application clock.
     *
     * @return the value of {@code BUSINESS_ZONE}
     */
    public ZoneId zone() {
        final long __gokulMethodStartedNanos = MethodTiming.start(ApplicationClock.class, "zone()");
        try {
            return BUSINESS_ZONE;
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, ApplicationClock.class, "zone()");
        }
    }

    /**
     * Returns the current local date and time in the business timezone.
     *
     * @return the value of {@code LocalDateTime.now(BUSINESS_ZONE)}
     */
    public LocalDateTime now() {
        final long __gokulMethodStartedNanos = MethodTiming.start(ApplicationClock.class, "now()");
        try {
            return LocalDateTime.now(BUSINESS_ZONE);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, ApplicationClock.class, "now()");
        }
    }

    /**
     * Returns today information for application clock.
     *
     * @return the value of {@code LocalDate.now(BUSINESS_ZONE)}
     */
    public LocalDate today() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ApplicationClock.class, "today()");
        try {
            return LocalDate.now(BUSINESS_ZONE);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, ApplicationClock.class, "today()");
        }
    }

    /**
     * Currents time.
     *
     * @return the current time result
     */
    public LocalTime currentTime() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ApplicationClock.class, "currentTime()");
        try {
            return LocalTime.now(BUSINESS_ZONE);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, ApplicationClock.class, "currentTime()");
        }
    }
}
