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
    void qaFeaturesDefaultOnWithoutDisablingExistingInventory() throws IOException {
        var properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application.properties")) {
            assertThat(stream).isNotNull();
            properties.load(stream);
        }
        var enabledFeatures = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("gokul.features."))
                .filter(key -> !key.endsWith("future-ordering-days"))
                .filter(key -> !key.endsWith("branch-experience"))
                .filter(key -> !List.of("gokul.features.simplified-checkout", "gokul.features.bilingual-storefront", "gokul.features.admin-preparation-board").contains(key))
                .toList();
        assertThat(properties.getProperty("gokul.notifications.staff.email-enabled")).endsWith(":true}");
        assertThat(enabledFeatures).hasSizeGreaterThan(30);
        for (var feature : enabledFeatures) {
            assertThat(properties.getProperty(feature)).endsWith(":true}");
        }
        for (var feature : List.of("simplified-checkout", "bilingual-storefront", "admin-preparation-board")) {
            assertThat(properties.getProperty("gokul.features." + feature)).endsWith(":true}");
        }
        assertThat(properties.getProperty("gokul.features.customer-account-hub")).endsWith(":true}");
        assertThat(properties.getProperty("gokul.features.branch-experience")).endsWith(":true}");
        assertThat(properties.getProperty("gokul.features.planned-pickup-production")).endsWith(":true}");
        assertThat(properties.getProperty("gokul.features.planned-delivery-production")).endsWith(":true}");
        assertThat(properties.getProperty("gokul.features.occasion-enquiries")).endsWith(":true}");
        assertThat(properties.getProperty("gokul.features.occasion-payments")).endsWith(":true}");
        assertThat(properties.getProperty("gokul.features.occasion-bulk-production")).endsWith(":true}");
        assertThat(properties.getProperty("inventory.enforcement-enabled")).isEqualTo("true");
        assertThat(properties.getProperty("inventory.automation.scheduler-enabled")).isEqualTo("true");
        assertThat(properties.getProperty("gokul.identity.provider-abuse-controls-verified")).endsWith(":false}");
        assertThat(properties.getProperty("gokul.environment-isolation.enabled")).endsWith(":true}");
        assertThat(properties.getProperty("gokul.web.environment-cors-enabled")).endsWith(":true}");
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
            assertThat(features.isAcceptedCheckoutQuote()).isFalse();
            assertThat(features.isPreHomeIntentGateway()).isFalse();
            assertThat(features.isContextualStorefrontV2()).isFalse();
            assertThat(features.isPaymentReconciliationV2()).isFalse();
            assertThat(features.isTruthfulOrderTracking()).isFalse();
            assertThat(features.isCustomerAccountHub()).isFalse();
            assertThat(features.isBranchExperience()).isFalse();
            assertThat(features.isPlannedPickupProduction()).isFalse();
            assertThat(features.isPlannedDeliveryProduction()).isFalse();
            features.setFutureOrderingDays(0);
            assertThat(validator.validate(features)).isNotEmpty();
            features.setFutureOrderingDays(61);
            assertThat(validator.validate(features)).isNotEmpty();
        }
    }
}
