package com.gokulsweets.restaurant.config;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationTest {
    @Test
    void additiveFeaturesDefaultOffWithoutDisablingExistingInventory() throws IOException {
        var properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application.properties")) {
            assertThat(stream).isNotNull();
            properties.load(stream);
        }
        for (var feature : List.of("smart-availability", "smart-pickup-selection",
                "inventory-automation-v2", "customer-home-v2", "homepage-campaigns",
                "persistent-pickup-context", "cart-switch-preview", "authoritative-pickup-commitment")) {
            assertThat(properties.getProperty("gokul.features." + feature)).endsWith(":false}");
        }
        assertThat(properties.getProperty("inventory.enforcement-enabled")).isEqualTo("true");
        assertThat(properties.getProperty("inventory.automation.scheduler-enabled")).isEqualTo("true");
        assertThat(properties.getProperty("gokul.environment-isolation.enabled")).endsWith(":false}");
        assertThat(properties.getProperty("gokul.web.environment-cors-enabled")).endsWith(":false}");
    }

    @Test
    void disallowsWildcardOrPathOriginsAndInvalidAdvanceHorizon() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var cors = new WebCorsProperties();
            assertThat(validator.validate(cors)).isEmpty();
            cors.setAllowedOrigins(List.of("https://dev.gokulsweets.in", "https://gokulsweets.in"));
            assertThat(validator.validate(cors)).isEmpty();
            cors.setAllowedOrigins(List.of("*"));
            assertThat(validator.validate(cors)).isNotEmpty();
            cors.setAllowedOrigins(List.of("https://dev.gokulsweets.in/api"));
            assertThat(validator.validate(cors)).isNotEmpty();

            var features = new EnhancementProperties();
            assertThat(features.isSmartAvailability()).isFalse();
            assertThat(features.isPersistentPickupContext()).isFalse();
            assertThat(features.isCartSwitchPreview()).isFalse();
            assertThat(features.isAuthoritativePickupCommitment()).isFalse();
            features.setFutureOrderingDays(0);
            assertThat(validator.validate(features)).isNotEmpty();
            features.setFutureOrderingDays(61);
            assertThat(validator.validate(features)).isNotEmpty();
        }
    }
}
