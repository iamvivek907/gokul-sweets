package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerIdentityAvailabilityControllerTest {
    @Test
    void staysHiddenUntilAllServerGuardsAndSecretsAreConfigured() {
        var features = new EnhancementProperties();
        var settings = new MockEnvironment()
                .withProperty("gokul.environment-isolation.enabled", "true")
                .withProperty("gokul.web.environment-cors-enabled", "true")
                .withProperty("gokul.environment-isolation.environment", "DEV")
                .withProperty("gokul.msg91.server-authkey", "server-only-authkey")
                .withProperty("gokul.identity.rate-limit-key", "a-dedicated-key-with-at-least-thirty-two-characters");
        var controller = new CustomerIdentityAvailabilityController(features, settings);
        assertThat(controller.availability().getBody()).containsEntry("enabled", false);
        features.setCustomerOtpIdentity(true);
        assertThat(controller.availability().getBody()).containsEntry("enabled", true);
        settings.setProperty("gokul.msg91.server-authkey", "");
        assertThat(controller.availability().getBody()).containsEntry("enabled", false);
    }
}
