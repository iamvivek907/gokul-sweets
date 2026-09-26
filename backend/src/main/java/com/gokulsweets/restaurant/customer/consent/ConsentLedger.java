package com.gokulsweets.restaurant.customer.consent;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/** Internal ledger. Do not expose until customer identity is verified server-side. */
@Service
@RequiredArgsConstructor
public class ConsentLedger {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public ConsentDecision current(ConsentEnvironment environment, UUID verifiedSubjectId,
                                   ConsentPurpose purpose) {
        requireSubject(verifiedSubjectId);
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(purpose, "purpose");
        return jdbc.query("""
                        SELECT granted, policy_version, recorded_at FROM customer_consent_events
                        WHERE environment = ? AND verified_subject_id = ? AND purpose = ?
                        ORDER BY id DESC LIMIT 1
                        """, rs -> rs.next()
                        ? new ConsentDecision(rs.getBoolean(1), rs.getString(2),
                        rs.getTimestamp(3).toInstant())
                        : new ConsentDecision(false, "", null),
                environment.name(), verifiedSubjectId, purpose.name());
    }

    @Transactional
    public ConsentDecision record(ConsentEnvironment environment, UUID verifiedSubjectId,
                                  ConsentPurpose purpose, String policyVersion, boolean granted) {
        requireSubject(verifiedSubjectId);
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(purpose, "purpose");
        if (policyVersion == null || !policyVersion.matches("[A-Za-z0-9._-]{1,40}")) {
            throw new IllegalArgumentException("A valid consent policy version is required");
        }
        // Serialize decisions for this subject and purpose; a withdrawal cannot be
        // reordered behind an earlier concurrent grant. Both calls use one transaction.
        String lockKey = environment.name() + ":" + verifiedSubjectId + ":" + purpose.name();
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> { rs.next(); return null; }, lockKey);
        jdbc.update("""
                        INSERT INTO customer_consent_events
                        (environment, verified_subject_id, purpose, policy_version, granted, actor)
                        VALUES (?, ?, ?, ?, ?, 'CUSTOMER')
                        """, environment.name(), verifiedSubjectId, purpose.name(), policyVersion, granted);
        return current(environment, verifiedSubjectId, purpose);
    }

    private static void requireSubject(UUID subjectId) {
        if (subjectId == null) {
            throw new IllegalArgumentException("A verified customer subject is required");
        }
    }
}
