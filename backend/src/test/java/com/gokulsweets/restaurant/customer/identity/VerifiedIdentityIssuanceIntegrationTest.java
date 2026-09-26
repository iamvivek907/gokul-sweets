package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.consent.ConsentLedger;
import com.gokulsweets.restaurant.customer.consent.ConsentPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "gokul.identity.phone-only-order-recovery=true")
@Transactional
class VerifiedIdentityIssuanceIntegrationTest {
    @Autowired VerifiedIdentityIssuance issuance;
    @Autowired VerifiedCustomerSessionStore sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsentLedger consent;

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

    @Test
    void reverifiedPhoneGetsFreshSubjectAndCannotInheritSessionsOrConsent() {
        var now = Instant.parse("2026-09-26T18:29:59Z"); // just before IST midnight
        var phone = "+919876543210";
        var first = issuance.issue(ConsentEnvironment.DEV, "first-" + java.util.UUID.randomUUID(), phone, now);
        var oldSubject = sessions.subject(ConsentEnvironment.DEV, first.token(), now).orElseThrow();
        consent.record(ConsentEnvironment.DEV, oldSubject, ConsentPurpose.MARKETING, "2026-09", true);

        var second = issuance.issue(ConsentEnvironment.DEV, "second-" + java.util.UUID.randomUUID(),
                phone, now.plusSeconds(2));
        var newSubject = sessions.subject(ConsentEnvironment.DEV, second.token(), now.plusSeconds(2)).orElseThrow();
        assertThat(newSubject).isNotEqualTo(oldSubject);
        assertThat(sessions.subject(ConsentEnvironment.DEV, first.token(), now.plusSeconds(2))).isEmpty();
        assertThat(consent.current(ConsentEnvironment.DEV, newSubject, ConsentPurpose.MARKETING).granted())
                .isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM verified_subject_rotations
                WHERE environment = 'DEV' AND prior_subject_id = ? AND new_subject_id = ?
                """, Integer.class, oldSubject, newSubject)).isEqualTo(1);
    }

    @Test
    void phoneOtpAloneRecoversVerifiedOrdersAcrossDevicesAndAuditsEachTransfer() {
        var now = Instant.now();
        var phone = "+919876543210";
        var first = issuance.issue(ConsentEnvironment.DEV, "first-" + java.util.UUID.randomUUID(), phone, now);
        var oldSubject = sessions.subject(ConsentEnvironment.DEV, first.token(), now).orElseThrow();
        var marker = java.util.UUID.randomUUID().toString().substring(0, 8);
        var branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Test branch') RETURNING id",
                Long.class, "IDENTITY-" + marker);
        var slot = jdbc.queryForObject("""
                INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time, capacity)
                VALUES (?, CURRENT_DATE, '10:00', '10:30', 2) RETURNING id
                """, Long.class, branch);
        var orderNumber = "GKS-IDENTITY-" + marker;
        var order = jdbc.queryForObject("""
                INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name,
                    customer_phone, pickup_type, order_status, reservation_expires_at)
                VALUES (?, ?, ?, 'Test Customer', '9876543210', 'NORMAL', 'PENDING_PAYMENT', CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, orderNumber, branch, slot);
        jdbc.update("""
                INSERT INTO verified_order_ownership(order_id, environment, verified_subject_id)
                VALUES (?, 'DEV', ?)
                """, order, oldSubject);
        var guestOrder = jdbc.queryForObject("""
                INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name,
                    customer_phone, pickup_type, order_status, reservation_expires_at)
                VALUES (?, ?, ?, 'Guest Customer', '9876543210', 'NORMAL', 'PENDING_PAYMENT', CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, orderNumber + "-GUEST", branch, slot);

        var continued = issuance.issue(ConsentEnvironment.DEV, "continued-" + java.util.UUID.randomUUID(),
                phone, now.plusSeconds(1));
        var continuedSubject = sessions.subject(ConsentEnvironment.DEV, continued.token(), now.plusSeconds(1))
                .orElseThrow();
        assertThat(continuedSubject).isNotEqualTo(oldSubject);
        assertThat(jdbc.queryForObject("""
                SELECT verified_subject_id FROM verified_order_ownership WHERE order_id = ?
                """, java.util.UUID.class, order)).isEqualTo(continuedSubject);
        assertThat(sessions.subject(ConsentEnvironment.DEV, first.token(), now.plusSeconds(1))).isEmpty();

        var newHolder = issuance.issue(ConsentEnvironment.DEV, "new-holder-" + java.util.UUID.randomUUID(),
                phone, now.plusSeconds(2));
        var newSubject = sessions.subject(ConsentEnvironment.DEV, newHolder.token(), now.plusSeconds(2))
                .orElseThrow();
        assertThat(newSubject).isNotEqualTo(continuedSubject);
        assertThat(jdbc.queryForObject("""
                SELECT verified_subject_id FROM verified_order_ownership WHERE order_id = ?
                """, java.util.UUID.class, order)).isEqualTo(newSubject);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM verified_order_transfer_audit
                WHERE environment = 'DEV' AND order_id = ?
                """, Integer.class, order)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM verified_order_ownership WHERE order_id = ?
                """, Integer.class, guestOrder)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT sum(transferred_order_count) FROM verified_subject_rotations
                WHERE environment = 'DEV' AND new_subject_id IN (?, ?)
                """, Long.class, continuedSubject, newSubject)).isEqualTo(2L);
    }
}
