package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
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

    @Transactional
    public UUID recordVerifiedPhone(ConsentEnvironment environment, String verifiedPhone, Instant now) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(now);
        if (verifiedPhone == null || !verifiedPhone.matches("\\+91[6-9][0-9]{9}")) {
            throw new IllegalArgumentException("A provider-verified Indian mobile is required");
        }
        // Serialize first verification and repeat verification across app instances.
        // A reused phone must never inherit a prior holder's sessions or consent.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> { rs.next(); return null; }, environment.name() + ":" + verifiedPhone);
        UUID prior = jdbc.query("""
                SELECT id FROM verified_customer_subjects
                WHERE environment = ? AND verified_phone = ?
                """, rs -> rs.next() ? (UUID) rs.getObject(1) : null,
                environment.name(), verifiedPhone);
        UUID current = UUID.randomUUID();
        if (prior == null) {
            jdbc.update("""
                    INSERT INTO verified_customer_subjects
                        (id, environment, verified_phone, created_at, last_verified_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, current, environment.name(), verifiedPhone,
                    Timestamp.from(now), Timestamp.from(now));
        } else {
            jdbc.update("""
                    UPDATE verified_customer_sessions SET revoked_at = ?
                    WHERE environment = ? AND verified_subject_id = ? AND revoked_at IS NULL
                    """, Timestamp.from(now), environment.name(), prior);
            jdbc.update("""
                    UPDATE verified_customer_subjects SET id = ?, last_verified_at = GREATEST(last_verified_at, ?)
                    WHERE environment = ? AND verified_phone = ?
                    """, current, Timestamp.from(now), environment.name(), verifiedPhone);
            jdbc.update("""
                    INSERT INTO verified_subject_rotations
                        (environment, prior_subject_id, new_subject_id, rotated_at, reason)
                    VALUES (?, ?, ?, ?, 'PHONE_REVERIFICATION')
                    """, environment.name(), prior, current, Timestamp.from(now));
        }
        return current;
    }
}
