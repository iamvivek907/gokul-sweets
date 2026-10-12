package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal registry. Call only with a mobile returned by server-side MSG91 proof verification. */
@Service
@RequiredArgsConstructor
public class VerifiedCustomerSubjectStore {

    private final JdbcTemplate jdbc;

    /**
     * Records verified phone.
     *
     * @param environment the environment
     * @param verifiedPhone the verified phone
     * @param now the now
     * @return the record verified phone result
     */
    @Transactional
    public UUID recordVerifiedPhone(
            ConsentEnvironment environment, String verifiedPhone, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedCustomerSubjectStore.class,
                        "recordVerifiedPhone(ConsentEnvironment,String,Instant)");
        try {
            return recordVerifiedPhone(environment, verifiedPhone, now, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedCustomerSubjectStore.class,
                    "recordVerifiedPhone(ConsentEnvironment,String,Instant)");
        }
    }

    /**
     * Records verified phone.
     *
     * @param environment the environment
     * @param verifiedPhone the verified phone
     * @param now the now
     * @param previousSessionSubject the previous session subject
     * @return the record verified phone result
     */
    @Transactional
    public UUID recordVerifiedPhone(
            ConsentEnvironment environment,
            String verifiedPhone,
            Instant now,
            UUID previousSessionSubject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedCustomerSubjectStore.class,
                        "recordVerifiedPhone(ConsentEnvironment,String,Instant,UUID)");
        try {
            Objects.requireNonNull(environment);
            Objects.requireNonNull(now);
            if (verifiedPhone == null || !verifiedPhone.matches("\\+91[6-9][0-9]{9}")) {
                throw new IllegalArgumentException("A provider-verified Indian mobile is required");
            }
            // One account per provider-verified phone; sessions remain device-specific.
            jdbc.query(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                    rs -> {
                        rs.next();
                        return null;
                    },
                    environment.name() + ":" + verifiedPhone);
            UUID current =
                    jdbc.queryForObject(
                            """
INSERT INTO verified_customer_subjects
    (id, environment, verified_phone, created_at, last_verified_at)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT (environment, verified_phone) DO UPDATE
    SET last_verified_at = GREATEST(verified_customer_subjects.last_verified_at,
                                   EXCLUDED.last_verified_at)
RETURNING id
""",
                            UUID.class,
                            UUID.randomUUID(),
                            environment.name(),
                            verifiedPhone,
                            Timestamp.from(now),
                            Timestamp.from(now));
            // Only provider proof reaches this method. Typed checkout phones never promote a
            // contact.
            jdbc.update(
                    """
                    UPDATE customer_contacts SET verification_status='VERIFIED', updated_at=?
                    WHERE normalized_phone=? AND verification_status='UNVERIFIED'
                    """,
                    Timestamp.valueOf(
                            java.time.LocalDateTime.ofInstant(
                                    now, java.time.ZoneId.of("Asia/Kolkata"))),
                    verifiedPhone);
            return current;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedCustomerSubjectStore.class,
                    "recordVerifiedPhone(ConsentEnvironment,String,Instant,UUID)");
        }
    }
}
