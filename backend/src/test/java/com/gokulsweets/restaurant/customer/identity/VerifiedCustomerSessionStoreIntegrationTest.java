package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;
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

    @Test
    void sessionExpiresAtSameInstantAcrossIstMidnightOnNonIndianServer() {
        var originalZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            var issuedAt = Instant.parse("2026-09-26T18:29:59Z"); // 23:59:59 IST
            var afterIstMidnight = issuedAt.plusSeconds(2);
            assertThat(issuedAt.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate())
                    .isNotEqualTo(afterIstMidnight.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate());
            var subject = UUID.randomUUID();
            var issued = sessions.issue(ConsentEnvironment.DEV, subject, issuedAt);
            assertThat(issued.expiresAt()).isEqualTo(issuedAt.plusSeconds(7 * 24 * 60 * 60));
            assertThat(sessions.subject(ConsentEnvironment.DEV, issued.token(), afterIstMidnight))
                    .contains(subject);
            assertThat(sessions.subject(ConsentEnvironment.DEV, issued.token(), issued.expiresAt().minusSeconds(1)))
                    .contains(subject);
            assertThat(sessions.subject(ConsentEnvironment.DEV, issued.token(), issued.expiresAt()))
                    .isEmpty();
        } finally {
            TimeZone.setDefault(originalZone);
        }
    }
}
