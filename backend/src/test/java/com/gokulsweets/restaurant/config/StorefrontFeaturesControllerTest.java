package com.gokulsweets.restaurant.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontFeaturesControllerTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-25T18:31:00Z"), ZoneId.of("Asia/Kolkata"));

    @Test
    void contextDisplayDefaultsOffAndUsesIndiaBusinessDate() {
        var properties = new EnhancementProperties();
        var result = new StorefrontFeaturesController(properties, clock).features();
        assertThat(result.persistentPickupContext()).isFalse();
        assertThat(result.cartSwitchPreview()).isFalse();
        assertThat(result.paidCartRecovery()).isFalse();
        assertThat(result.authoritativePickupCommitment()).isFalse();
        assertThat(result.today()).isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    void contextDisplayCanBeEnabledWithoutAvailabilityOrChangingExistingFlags() {
        var properties = new EnhancementProperties();
        properties.setPersistentPickupContext(true);
        var result = new StorefrontFeaturesController(properties, clock).features();
        assertThat(result.persistentPickupContext()).isTrue();
        assertThat(result.smartAvailability()).isFalse();
        assertThat(result.smartPickupSelection()).isFalse();
        assertThat(result.cartSwitchPreview()).isFalse();
        properties.setCartSwitchPreview(true);
        properties.setPaidCartRecovery(true);
        var previewEnabled = new StorefrontFeaturesController(properties, clock).features();
        assertThat(previewEnabled.cartSwitchPreview()).isTrue();
        assertThat(previewEnabled.paidCartRecovery()).isTrue();
        assertThat(previewEnabled.smartAvailability()).isFalse();
        properties.setAuthoritativePickupCommitment(true);
        assertThat(new StorefrontFeaturesController(properties, clock).features().authoritativePickupCommitment()).isFalse();
        properties.setSmartAvailability(true);
        assertThat(new StorefrontFeaturesController(properties, clock).features().authoritativePickupCommitment()).isTrue();
    }
}
