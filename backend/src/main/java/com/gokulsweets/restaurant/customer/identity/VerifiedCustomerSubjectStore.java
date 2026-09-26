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
        // One statement handles competing verifications and preserves the existing subject.
        // Do not infer ownership of historic orders or consent from this association.
        return jdbc.queryForObject("""
                INSERT INTO verified_customer_subjects
                    (id, environment, verified_phone, created_at, last_verified_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (environment, verified_phone)
                DO UPDATE SET last_verified_at = GREATEST(
                    verified_customer_subjects.last_verified_at, EXCLUDED.last_verified_at)
                RETURNING id
                """, UUID.class, UUID.randomUUID(), environment.name(), verifiedPhone,
                Timestamp.from(now), Timestamp.from(now));
    }
}
