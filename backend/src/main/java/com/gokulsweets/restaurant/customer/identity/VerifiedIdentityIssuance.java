package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.observability.MethodTiming;

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

    /**
     * Issues verified identity issuance data and returns the {@code
     * VerifiedCustomerSessionStore.IssuedSession} result.
     *
     * @param environment the environment supplied to this method
     * @param accessToken the access token supplied to this method
     * @param verifiedPhone the verified phone supplied to this method
     * @param now the now supplied to this method
     * @return the value of {@code issue(environment, accessToken, verifiedPhone, null, now)}
     */
    @Transactional
    public VerifiedCustomerSessionStore.IssuedSession issue(
            ConsentEnvironment environment, String accessToken, String verifiedPhone, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedIdentityIssuance.class,
                        "issue(ConsentEnvironment,String,String,Instant)");
        try {
            return issue(environment, accessToken, verifiedPhone, null, now);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedIdentityIssuance.class,
                    "issue(ConsentEnvironment,String,String,Instant)");
        }
    }

    /**
     * Issues verified identity issuance data and returns the {@code
     * VerifiedCustomerSessionStore.IssuedSession} result.
     *
     * <p>Writes {@code verified_identity_proof_claims}.
     *
     * @param environment the environment supplied to this method
     * @param accessToken the access token supplied to this method
     * @param verifiedPhone the verified phone supplied to this method
     * @param previousSessionToken the previous session token supplied to this method
     * @param now the now supplied to this method
     * @return the value of {@code sessions.issue(environment, subject, now)}
     * @throws IllegalArgumentException when the method rejects the request with {@code A
     *     provider-verified mobile is required}; {@code Invalid identity proof}
     * @throws IllegalStateException when the method rejects the request with {@code Identity proof
     *     already used}; {@code Identity proof hashing unavailable}
     */
    @Transactional
    public VerifiedCustomerSessionStore.IssuedSession issue(
            ConsentEnvironment environment,
            String accessToken,
            String verifiedPhone,
            String previousSessionToken,
            Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        VerifiedIdentityIssuance.class,
                        "issue(ConsentEnvironment,String,String,String,Instant)");
        try {
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
                digest =
                        MessageDigest.getInstance("SHA-256")
                                .digest(accessToken.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("Identity proof hashing unavailable", e);
            }
            try {
                // The primary key serializes competing consumers even across app instances.
                jdbc.queryForObject(
                        """
INSERT INTO verified_identity_proof_claims (proof_digest, environment, claimed_at)
VALUES (?, ?, ?)
ON CONFLICT (proof_digest) DO NOTHING
RETURNING environment
""",
                        String.class,
                        digest,
                        environment.name(),
                        Timestamp.from(now));
            } catch (EmptyResultDataAccessException e) {
                throw new IllegalStateException("Identity proof already used", e);
            }
            var previousSubject =
                    sessions.subject(environment, previousSessionToken, now).orElse(null);
            var subject =
                    subjects.recordVerifiedPhone(environment, verifiedPhone, now, previousSubject);
            return sessions.issue(environment, subject, now);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    VerifiedIdentityIssuance.class,
                    "issue(ConsentEnvironment,String,String,String,Instant)");
        }
    }
}
