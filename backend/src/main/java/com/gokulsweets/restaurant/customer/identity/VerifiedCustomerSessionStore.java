package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Internal session store. Issue only after MSG91 proof is verified server-side. */
@Service
@RequiredArgsConstructor
public class VerifiedCustomerSessionStore {
    private static final Duration LIFETIME = Duration.ofDays(7);
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public IssuedSession issue(ConsentEnvironment environment, UUID verifiedSubjectId, Instant now) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(verifiedSubjectId);
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var token = HexFormat.of().formatHex(bytes);
        jdbc.update("""
                INSERT INTO verified_customer_sessions
                (environment, verified_subject_id, token_digest, issued_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """, environment.name(), verifiedSubjectId, digest(token),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plus(LIFETIME)));
        return new IssuedSession(token, now.plus(LIFETIME));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> subject(ConsentEnvironment environment, String token, Instant now) {
        Objects.requireNonNull(environment);
        if (token == null || !token.matches("[0-9a-f]{64}")) return Optional.empty();
        return jdbc.query("""
                SELECT verified_subject_id FROM verified_customer_sessions
                WHERE environment = ? AND token_digest = ? AND expires_at > ? AND revoked_at IS NULL
                """, rs -> rs.next() ? Optional.of((UUID) rs.getObject(1)) : Optional.empty(),
                environment.name(), digest(token), java.sql.Timestamp.from(now));
    }

    @Transactional
    public void revoke(ConsentEnvironment environment, String token, Instant now) {
        Objects.requireNonNull(environment);
        if (token == null || !token.matches("[0-9a-f]{64}")) return;
        jdbc.update("""
                UPDATE verified_customer_sessions SET revoked_at = ?
                WHERE environment = ? AND token_digest = ? AND revoked_at IS NULL
                """, java.sql.Timestamp.from(now), environment.name(), digest(token));
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Session hashing unavailable", e);
        }
    }

    /** The bearer token must be delivered only through a secure HttpOnly cookie. */
    public record IssuedSession(String token, Instant expiresAt) {
        @Override public String toString() { return "IssuedSession[token=REDACTED]"; }
    }
}
