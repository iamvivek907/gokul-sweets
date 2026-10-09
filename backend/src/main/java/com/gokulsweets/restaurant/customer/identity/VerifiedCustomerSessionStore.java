package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.observability.MethodTiming;

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

    // Remember a verified customer across normal return visits, with an
    // absolute limit so a lost device cannot retain access indefinitely.
    private static final Duration LIFETIME = Duration.ofDays(30);

    private final JdbcTemplate jdbc;

    private final SecureRandom random = new SecureRandom();

    /**
     * Issues verified customer session store data and returns the {@code IssuedSession} result.
     *
     * <p>Writes {@code verified_customer_sessions}.
     *
     * @param environment the environment supplied to this method
     * @param verifiedSubjectId the verified subject id supplied to this method
     * @param now the now supplied to this method
     * @return the {@code IssuedSession} result
     */
    @Transactional
    public IssuedSession issue(
            ConsentEnvironment environment, UUID verifiedSubjectId, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedCustomerSessionStore.class,
                        "issue(ConsentEnvironment,UUID,Instant)");
        try {
            Objects.requireNonNull(environment);
            Objects.requireNonNull(verifiedSubjectId);
            var bytes = new byte[32];
            random.nextBytes(bytes);
            var token = HexFormat.of().formatHex(bytes);
            jdbc.update(
                    """
                    INSERT INTO verified_customer_sessions
                    (environment, verified_subject_id, token_digest, issued_at, expires_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    environment.name(),
                    verifiedSubjectId,
                    digest(token),
                    java.sql.Timestamp.from(now),
                    java.sql.Timestamp.from(now.plus(LIFETIME)));
            return new IssuedSession(token, now.plus(LIFETIME));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedCustomerSessionStore.class,
                    "issue(ConsentEnvironment,UUID,Instant)");
        }
    }

    /**
     * Returns subject information for verified customer session store.
     *
     * <p>Reads {@code verified_customer_sessions}.
     *
     * @param environment the environment supplied to this method
     * @param token the token supplied to this method
     * @param now the now supplied to this method
     * @return the {@code Optional<UUID>} result
     */
    @Transactional(readOnly = true)
    public Optional<UUID> subject(ConsentEnvironment environment, String token, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedCustomerSessionStore.class,
                        "subject(ConsentEnvironment,String,Instant)");
        try {
            Objects.requireNonNull(environment);
            if (token == null || !token.matches("[0-9a-f]{64}")) return Optional.empty();
            return jdbc.query(
                    """
SELECT verified_subject_id FROM verified_customer_sessions
WHERE environment = ? AND token_digest = ? AND expires_at > ? AND revoked_at IS NULL
""",
                    rs -> rs.next() ? Optional.of((UUID) rs.getObject(1)) : Optional.empty(),
                    environment.name(),
                    digest(token),
                    java.sql.Timestamp.from(now));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedCustomerSessionStore.class,
                    "subject(ConsentEnvironment,String,Instant)");
        }
    }

    /**
     * Revokes verified customer session store data.
     *
     * <p>Writes {@code verified_customer_sessions}.
     *
     * @param environment the environment supplied to this method
     * @param token the token supplied to this method
     * @param now the now supplied to this method
     */
    @Transactional
    public void revoke(ConsentEnvironment environment, String token, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedCustomerSessionStore.class,
                        "revoke(ConsentEnvironment,String,Instant)");
        try {
            Objects.requireNonNull(environment);
            if (token == null || !token.matches("[0-9a-f]{64}")) return;
            jdbc.update(
                    """
                    UPDATE verified_customer_sessions SET revoked_at = ?
                    WHERE environment = ? AND token_digest = ? AND revoked_at IS NULL
                    """,
                    java.sql.Timestamp.from(now),
                    environment.name(),
                    digest(token));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedCustomerSessionStore.class,
                    "revoke(ConsentEnvironment,String,Instant)");
        }
    }

    /**
     * Returns the SHA-256 digest of the supplied value as bytes.
     *
     * @param token the token supplied to this method
     * @return the {@code byte[]} result
     * @throws IllegalStateException when the method rejects the request with {@code Session hashing
     *     unavailable}
     */
    static byte[] digest(String token) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(VerifiedCustomerSessionStore.class, "digest(String)");
        try {
            try {
                return MessageDigest.getInstance("SHA-256")
                        .digest(token.getBytes(StandardCharsets.US_ASCII));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("Session hashing unavailable", e);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedCustomerSessionStore.class,
                    "digest(String)");
        }
    }

    /**
     * The bearer token must be delivered only through a secure HttpOnly cookie.
     *
     * @param token the token
     * @param expiresAt the expires at
     */
    public record IssuedSession(String token, Instant expiresAt) {

        /**
         * Tos string.
         *
         * @return the to string result
         */
        @Override
        public String toString() {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(
                            VerifiedCustomerSessionStore.IssuedSession.class, "toString()");
            try {
                return "IssuedSession[token=REDACTED]";
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        VerifiedCustomerSessionStore.IssuedSession.class,
                        "toString()");
            }
        }
    }
}
