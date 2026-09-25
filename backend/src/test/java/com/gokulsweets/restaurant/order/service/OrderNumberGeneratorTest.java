package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNumberGeneratorTest {
    @Test
    void usesIndiaBusinessDateAcrossUtcMidnightAndYearBoundary() {
        var features = new EnhancementProperties();
        var before = new OrderNumberGenerator(
                Clock.fixed(Instant.parse("2026-12-31T18:29:59Z"), ZoneOffset.UTC), features);
        var after = new OrderNumberGenerator(
                Clock.fixed(Instant.parse("2026-12-31T18:30:00Z"), ZoneOffset.UTC), features);

        assertThat(before.generate()).startsWith("GKS-20261231-");
        assertThat(after.generate()).startsWith("GKS-20270101-");
        assertThat(after.generate()).matches("GKS-20270101-[0-9A-F]{16}");
        features.setIstTimeFixEnabled(false);
        assertThat(after.generate()).startsWith("GKS-" +
                java.time.LocalDate.ofInstant(Instant.parse("2026-12-31T18:30:00Z"),
                        java.time.ZoneId.systemDefault()).format(
                        java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "-");
    }
}
