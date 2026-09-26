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
        assertThat(result.paymentPollingV2()).isFalse();
        assertThat(result.inPlaceBranchSwitch()).isFalse();
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
        properties.setPaymentPollingV2(true);
        properties.setPreHomeIntentGateway(true);
        properties.setContextualStorefrontV2(true);
        properties.setControlledCampaignPublishing(true);
        properties.setInPlaceBranchSwitch(true);
        var previewEnabled = new StorefrontFeaturesController(properties, clock).features();
        assertThat(previewEnabled.cartSwitchPreview()).isTrue();
        assertThat(previewEnabled.paidCartRecovery()).isTrue();
        assertThat(previewEnabled.paymentPollingV2()).isTrue();
        assertThat(previewEnabled.preHomeIntentGateway()).isTrue();
        assertThat(previewEnabled.contextualStorefrontV2()).isTrue();
        assertThat(previewEnabled.controlledCampaignPublishing()).isTrue();
        assertThat(previewEnabled.inPlaceBranchSwitch()).isTrue();
        assertThat(previewEnabled.smartAvailability()).isFalse();
        properties.setAuthoritativePickupCommitment(true);
        assertThat(new StorefrontFeaturesController(properties, clock).features().authoritativePickupCommitment()).isFalse();
        properties.setSmartAvailability(true);
        assertThat(new StorefrontFeaturesController(properties, clock).features().authoritativePickupCommitment()).isTrue();
    }
}
