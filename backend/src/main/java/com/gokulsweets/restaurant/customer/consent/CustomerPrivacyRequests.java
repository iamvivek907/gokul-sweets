package com.gokulsweets.restaurant.customer.consent;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Request intake only. No financial records or customer data are exported or erased here. */
@Service
@RequiredArgsConstructor
public class CustomerPrivacyRequests {

    private final JdbcTemplate jdbc;

    /**
     * Submits customer privacy requests data and returns the {@code Request} result.
     *
     * <p>Reads {@code customer_privacy_requests}.
     *
     * <p>Writes {@code customer_privacy_requests}.
     *
     * @param environment the environment supplied to this method
     * @param subject the subject supplied to this method
     * @param kind the kind supplied to this method
     * @return the {@code Request} result
     * @throws IllegalStateException when the method rejects the request with {@code Privacy request
     *     was not recorded}
     */
    @Transactional
    public Request submit(ConsentEnvironment environment, UUID subject, PrivacyRequestKind kind) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerPrivacyRequests.class,
                        "submit(ConsentEnvironment,UUID,PrivacyRequestKind)");
        try {
            requireIdentity(environment, subject);
            Objects.requireNonNull(kind);
            jdbc.update(
                    """
INSERT INTO customer_privacy_requests (environment, verified_subject_id, request_kind)
VALUES (?, ?, ?) ON CONFLICT (environment, verified_subject_id, request_kind) DO NOTHING
""",
                    environment.name(),
                    subject,
                    kind.name());
            return jdbc.query(
                    """
                    SELECT id, request_kind, received_at FROM customer_privacy_requests
                    WHERE environment = ? AND verified_subject_id = ? AND request_kind = ?
                    """,
                    rs -> {
                        if (!rs.next())
                            throw new IllegalStateException("Privacy request was not recorded");
                        return new Request(
                                rs.getLong(1),
                                PrivacyRequestKind.valueOf(rs.getString(2)),
                                rs.getTimestamp(3).toInstant());
                    },
                    environment.name(),
                    subject,
                    kind.name());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerPrivacyRequests.class,
                    "submit(ConsentEnvironment,UUID,PrivacyRequestKind)");
        }
    }

    /**
     * Fors subject.
     *
     * @param environment the environment
     * @param subject the subject
     * @return the for subject result
     */
    @Transactional(readOnly = true)
    public List<Request> forSubject(ConsentEnvironment environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerPrivacyRequests.class, "forSubject(ConsentEnvironment,UUID)");
        try {
            requireIdentity(environment, subject);
            return jdbc.query(
                    """
SELECT id, request_kind, received_at FROM customer_privacy_requests
WHERE environment = ? AND verified_subject_id = ? ORDER BY received_at DESC, id DESC
""",
                    (rs, row) ->
                            new Request(
                                    rs.getLong(1),
                                    PrivacyRequestKind.valueOf(rs.getString(2)),
                                    rs.getTimestamp(3).toInstant()),
                    environment.name(),
                    subject);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerPrivacyRequests.class,
                    "forSubject(ConsentEnvironment,UUID)");
        }
    }

    /**
     * Requires identity.
     *
     * @param environment the environment
     * @param subject the subject
     */
    private static void requireIdentity(ConsentEnvironment environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerPrivacyRequests.class, "requireIdentity(ConsentEnvironment,UUID)");
        try {
            Objects.requireNonNull(environment);
            Objects.requireNonNull(subject);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerPrivacyRequests.class,
                    "requireIdentity(ConsentEnvironment,UUID)");
        }
    }

    /**
     * Immutable request data contract.
     *
     * @param id the id
     * @param kind the kind
     * @param receivedAt the received at
     */
    public record Request(long id, PrivacyRequestKind kind, Instant receivedAt) {}
}
