package com.gokulsweets.restaurant.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePeProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentIsolationGuardTest {
    @Test
    void rolloutDefaultsOffWithoutBlockingExistingLocalDevelopment() {
        var guard = new EnvironmentIsolationGuard(new MockEnvironment(),
                new WebCorsProperties(), new PhonePeProperties());
        assertThatCode(guard::check).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(guard.phonePeMerchantOrderId(42L))
                .isEqualTo("GKS-PPE-42");
        var dev = new MockEnvironment()
                .withProperty("gokul.environment-isolation.enabled", "true")
                .withProperty("gokul.environment-isolation.environment", "DEV");
        org.assertj.core.api.Assertions.assertThat(new EnvironmentIsolationGuard(dev,
                new WebCorsProperties(), new PhonePeProperties()).phonePeMerchantOrderId(42L))
                .isEqualTo("GKS-DEV-PPE-42");
    }

    private EnvironmentIsolationGuard.Settings dev() {
        return new EnvironmentIsolationGuard.Settings("DEV", "https://api-dev.gokulsweets.in",
                List.of("https://dev.gokulsweets.in"), "https://dev.gokulsweets.in/checkout",
                "https://api-dev.gokulsweets.in/api/payments/webhooks/phonepe",
                "jdbc:postgresql://db-dev:5432/gokul_dev", "gokul-dev-images",
                "https://dev-images.example.in", "https://api-preprod.phonepe.com", "dev-client", "dev-key");
    }

    @Test
    void acceptsMatchingDevDeployment() {
        assertThatCode(() -> EnvironmentIsolationGuard.validate(dev())).doesNotThrowAnyException();
    }

    @Test
    void rejectsCrossEnvironmentReturnAndCors() {
        var d = dev();
        var badReturn = new EnvironmentIsolationGuard.Settings(d.deployment(), d.apiOrigin(),
                d.allowedOrigins(), "https://gokulsweets.in/checkout", d.webhookUrl(),
                d.databaseUrl(), d.bucket(), d.imageOrigin(), d.phonePeBase(),
                d.phonePeClientId(), d.webhookKeyId());
        assertThatThrownBy(() -> EnvironmentIsolationGuard.validate(badReturn))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("return base");
        var bothOrigins = new EnvironmentIsolationGuard.Settings(d.deployment(), d.apiOrigin(),
                List.of("https://dev.gokulsweets.in", "https://gokulsweets.in"), d.redirectBase(),
                d.webhookUrl(), d.databaseUrl(), d.bucket(), d.imageOrigin(), d.phonePeBase(),
                d.phonePeClientId(), d.webhookKeyId());
        assertThatThrownBy(() -> EnvironmentIsolationGuard.validate(bothOrigins))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("CORS");
    }

    @Test
    void rejectsProductionWebhookSharedBucketAndSandbox() {
        var d = dev();
        var badWebhook = new EnvironmentIsolationGuard.Settings(d.deployment(), d.apiOrigin(),
                d.allowedOrigins(), d.redirectBase(), "https://api.gokulsweets.in/api/payments/webhooks/phonepe",
                d.databaseUrl(), d.bucket(), d.imageOrigin(), d.phonePeBase(),
                d.phonePeClientId(), d.webhookKeyId());
        assertThatThrownBy(() -> EnvironmentIsolationGuard.validate(badWebhook))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("webhook");
        var sharedBucket = new EnvironmentIsolationGuard.Settings(d.deployment(), d.apiOrigin(),
                d.allowedOrigins(), d.redirectBase(), d.webhookUrl(), d.databaseUrl(),
                "gokul-sweets-images", d.imageOrigin(), d.phonePeBase(), d.phonePeClientId(),
                d.webhookKeyId());
        assertThatThrownBy(() -> EnvironmentIsolationGuard.validate(sharedBucket))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("R2 bucket");
        var productionSandbox = new EnvironmentIsolationGuard.Settings("PROD", "https://api.gokulsweets.in",
                List.of("https://gokulsweets.in"), "https://gokulsweets.in/checkout",
                "https://api.gokulsweets.in/api/payments/webhooks/phonepe", d.databaseUrl(),
                "gokul-prod-images", d.imageOrigin(), d.phonePeBase(), "prod-client", "prod-key");
        assertThatThrownBy(() -> EnvironmentIsolationGuard.validate(productionSandbox))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("sandbox");
    }
}
