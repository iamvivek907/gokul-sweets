package com.gokulsweets.restaurant.customer.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@SpringBootTest
@Transactional
class IdentityDeviceRegistryIntegrationTest {
    @Autowired IdentityDeviceRegistry devices;

    @Test
    void serverMintedDeviceExpiresAndCannotCrossEnvironments() {
        var now = Instant.parse("2026-09-26T18:29:59Z"); // IST midnight follows
        var token = devices.issue(ConsentEnvironment.DEV, now);
        assertThat(devices.recognized(ConsentEnvironment.DEV, token, now.plusSeconds(2))).isTrue();
        assertThat(devices.recognized(ConsentEnvironment.PROD, token, now)).isFalse();
        assertThat(devices.recognized(ConsentEnvironment.DEV, "a".repeat(64), now)).isFalse();
        assertThat(devices.recognized(ConsentEnvironment.DEV, token, now.plusSeconds(30L * 86400)))
                .isFalse();
    }
}
