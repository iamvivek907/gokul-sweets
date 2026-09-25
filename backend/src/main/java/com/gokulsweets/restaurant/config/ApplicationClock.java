package com.gokulsweets.restaurant.config;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class ApplicationClock {

    /** Local business dates and legacy database timestamps are interpreted in this zone. */
    public static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Kolkata");

    private static volatile boolean istTimeFixEnabled = true;

    public ApplicationClock(EnhancementProperties features) {
        istTimeFixEnabled = features.isIstTimeFixEnabled();
    }

    /**
     * Entity callbacks cannot inject a Clock. This rollout switch makes their
     * legacy LocalDateTime timestamps consistent with payment and slot logic.
     * Switch off only while investigating an existing database timestamp migration.
     */
    public static LocalDateTime legacyTimestampNow() {
        return istTimeFixEnabled
                ? LocalDateTime.now(BUSINESS_ZONE)
                : LocalDateTime.now();
    }

    public ZoneId zone() {
        return BUSINESS_ZONE;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    public LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    public LocalTime currentTime() {
        return LocalTime.now(BUSINESS_ZONE);
    }
}
