package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class VerifiedCustomerSessionStoreIntegrationTest {
    @Autowired VerifiedCustomerSessionStore sessions;

    @Test
    void tokenIsScopedExpiresAndRevokesWithoutStoringBearerValue() {
        var now = Instant.parse("2026-09-26T16:00:00Z");
        var subject = UUID.randomUUID();
        var issued = sessions.issue(ConsentEnvironment.DEV, subject, now);

        assertThat(issued.token()).matches("[0-9a-f]{64}");
        assertThat(issued.toString()).doesNotContain(issued.token());
        assertThat(sessions.subject(ConsentEnvironment.DEV, issued.token(), now))
                .contains(subject);
        assertThat(sessions.subject(ConsentEnvironment.PROD, issued.token(), now)).isEmpty();
        assertThat(sessions.subject(ConsentEnvironment.DEV, issued.token(), issued.expiresAt()))
                .isEmpty();
        assertThat(sessions.subject(ConsentEnvironment.DEV, "invalid", now)).isEmpty();

        sessions.revoke(ConsentEnvironment.DEV, issued.token(), now);
        assertThat(sessions.subject(ConsentEnvironment.DEV, issued.token(), now)).isEmpty();
    }
}
