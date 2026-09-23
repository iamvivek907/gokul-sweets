package com.gokulsweets.restaurant.config;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class ApplicationClock {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Kolkata");

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