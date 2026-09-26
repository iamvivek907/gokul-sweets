package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "gokul.identity.rate-limit-key=integration-test-key-with-at-least-thirty-two-characters")
@Transactional
class IdentityExchangeRateLimiterIntegrationTest {
    @Autowired IdentityExchangeRateLimiter limiter;

    @Test
    void sourceLimitIsSharedWithinWindowAndIsolatedByEnvironmentAndAddress() {
        var now = Instant.parse("2026-09-26T17:00:00Z");
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.checkSource(ConsentEnvironment.DEV, "192.0.2.5", now);
        }
        assertThatThrownBy(() -> limiter.checkSource(ConsentEnvironment.DEV, "192.0.2.5", now))
                .isInstanceOf(IdentityExchangeRateLimiter.Limited.class);
        limiter.checkSource(ConsentEnvironment.PROD, "192.0.2.5", now);
        limiter.checkSource(ConsentEnvironment.DEV, "192.0.2.6", now);
        limiter.checkSource(ConsentEnvironment.DEV, "192.0.2.5", now.plusSeconds(901));
    }

    @Test
    void phoneLimitRequiresVerifiedShapeAndHasAnIndependentWindow() {
        var now = Instant.parse("2026-09-26T17:00:00Z");
        assertThatThrownBy(() -> limiter.checkVerifiedPhone(ConsentEnvironment.DEV, "9876543210", now))
                .isInstanceOf(IllegalArgumentException.class);
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.checkVerifiedPhone(ConsentEnvironment.DEV, "+919876543210", now);
        }
        assertThatThrownBy(() -> limiter.checkVerifiedPhone(ConsentEnvironment.DEV, "+919876543210", now))
                .isInstanceOf(IdentityExchangeRateLimiter.Limited.class);
        limiter.checkVerifiedPhone(ConsentEnvironment.DEV, "+919876543210", now.plusSeconds(3601));
    }
}
