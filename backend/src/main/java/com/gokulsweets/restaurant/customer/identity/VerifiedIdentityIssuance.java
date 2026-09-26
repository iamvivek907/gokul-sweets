package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/** Transactionally consumes a verified provider proof and issues one session. */
@Service
@RequiredArgsConstructor
public class VerifiedIdentityIssuance {
    private final JdbcTemplate jdbc;
    private final VerifiedCustomerSubjectStore subjects;
    private final VerifiedCustomerSessionStore sessions;

    @Transactional
    public VerifiedCustomerSessionStore.IssuedSession issue(
            ConsentEnvironment environment, String accessToken, String verifiedPhone, Instant now) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(now);
        if (accessToken == null || accessToken.isBlank() || accessToken.length() > 4096) {
            throw new IllegalArgumentException("Invalid identity proof");
        }
        if (verifiedPhone == null || !verifiedPhone.matches("\\+91[6-9][0-9]{9}")) {
            throw new IllegalArgumentException("A provider-verified mobile is required");
        }
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(accessToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Identity proof hashing unavailable", e);
        }
        try {
            // The primary key serializes competing consumers even across app instances.
            jdbc.queryForObject("""
                    INSERT INTO verified_identity_proof_claims (proof_digest, environment, claimed_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT (proof_digest) DO NOTHING
                    RETURNING environment
                    """, String.class, digest, environment.name(), Timestamp.from(now));
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("Identity proof already used", e);
        }
        var subject = subjects.recordVerifiedPhone(environment, verifiedPhone, now);
        return sessions.issue(environment, subject, now);
    }
}
