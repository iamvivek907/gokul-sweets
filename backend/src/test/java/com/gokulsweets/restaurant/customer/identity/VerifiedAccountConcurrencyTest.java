package com.gokulsweets.restaurant.customer.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;

/** Concurrent OTP logins share one account while receiving independent bearer sessions. */
@SpringBootTest
class VerifiedAccountConcurrencyTest {
    @Autowired VerifiedIdentityIssuance issuance;
    @Autowired VerifiedCustomerSessionStore sessions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void simultaneousFreshDeviceLoginsShareOneAccount() throws Exception {
        String phone =
                "+91"
                        + (6000000000L
                                + Math.floorMod(
                                        UUID.randomUUID().getLeastSignificantBits(), 3999999999L));
        Instant now = Instant.now();
        var executor = Executors.newFixedThreadPool(6);
        var gate = new CountDownLatch(1);
        var results = new ArrayList<Future<VerifiedCustomerSessionStore.IssuedSession>>();
        var proofs = new ArrayList<String>();
        try {
            for (int n = 0; n < 6; n++) {
                String proof = "concurrent-" + UUID.randomUUID();
                proofs.add(proof);
                results.add(
                        executor.submit(
                                () -> {
                                    gate.await();
                                    return issuance.issue(
                                            ConsentEnvironment.DEV, proof, phone, now);
                                }));
            }
            gate.countDown();
            var issued = new ArrayList<VerifiedCustomerSessionStore.IssuedSession>();
            for (var future : results) issued.add(future.get(30, TimeUnit.SECONDS));
            UUID account =
                    sessions.subject(ConsentEnvironment.DEV, issued.getFirst().token(), now)
                            .orElseThrow();
            assertThat(
                            issued.stream()
                                    .map(VerifiedCustomerSessionStore.IssuedSession::token)
                                    .distinct()
                                    .count())
                    .isEqualTo(6);
            for (var device : issued)
                assertThat(sessions.subject(ConsentEnvironment.DEV, device.token(), now))
                        .contains(account);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM verified_customer_subjects WHERE"
                                            + " environment='DEV' AND verified_phone=?",
                                    Integer.class,
                                    phone))
                    .isEqualTo(1);
            sessions.revoke(ConsentEnvironment.DEV, issued.getFirst().token(), now);
            assertThat(sessions.subject(ConsentEnvironment.DEV, issued.getFirst().token(), now))
                    .isEmpty();
            for (var device : issued.subList(1, issued.size()))
                assertThat(sessions.subject(ConsentEnvironment.DEV, device.token(), now))
                        .contains(account);
        } finally {
            gate.countDown();
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
            jdbc.update(
                    "DELETE FROM verified_customer_sessions WHERE environment='DEV' AND"
                        + " verified_subject_id IN (SELECT id FROM verified_customer_subjects WHERE"
                        + " environment='DEV' AND verified_phone=?)",
                    phone);
            jdbc.update(
                    "DELETE FROM verified_customer_subjects WHERE environment='DEV' AND"
                            + " verified_phone=?",
                    phone);
            for (var proof : proofs)
                jdbc.update(
                        "DELETE FROM verified_identity_proof_claims WHERE proof_digest=?",
                        VerifiedCustomerSessionStore.digest(proof));
        }
    }
}
