package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class VerifiedIdentityIssuanceIntegrationTest {
    @Autowired VerifiedIdentityIssuance issuance;
    @Autowired VerifiedCustomerSessionStore sessions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void oneProofIssuesOneSessionAndCannotBeReplayedAcrossEnvironments() {
        var now = Instant.parse("2026-09-26T17:00:00Z");
        var token = "provider-proof-" + java.util.UUID.randomUUID();
        var issued = issuance.issue(ConsentEnvironment.DEV, token, "+919876543210", now);
        var subject = sessions.subject(ConsentEnvironment.DEV, issued.token(), now).orElseThrow();
        assertThat(sessions.subject(ConsentEnvironment.PROD, issued.token(), now)).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM verified_customer_subjects
                WHERE environment = 'DEV' AND id = ?
                """, Integer.class, subject)).isEqualTo(1);
        assertThatThrownBy(() -> issuance.issue(ConsentEnvironment.DEV, token, "+919876543210", now))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> issuance.issue(ConsentEnvironment.PROD, token, "+919876543210", now))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedProofOrPhoneBeforeCreatingClaim() {
        var now = Instant.parse("2026-09-26T17:00:00Z");
        assertThatThrownBy(() -> issuance.issue(ConsentEnvironment.DEV, "", "+919876543210", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> issuance.issue(ConsentEnvironment.DEV, "another-proof", "9876543210", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM verified_identity_proof_claims", Integer.class))
                .isZero();
    }
}
