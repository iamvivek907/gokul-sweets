package com.gokulsweets.restaurant.customer.consent;

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

    @Transactional
    public Request submit(ConsentEnvironment environment, UUID subject, PrivacyRequestKind kind) {
        requireIdentity(environment, subject);
        Objects.requireNonNull(kind);
        jdbc.update("""
                INSERT INTO customer_privacy_requests (environment, verified_subject_id, request_kind)
                VALUES (?, ?, ?) ON CONFLICT (environment, verified_subject_id, request_kind) DO NOTHING
                """, environment.name(), subject, kind.name());
        return jdbc.query("""
                SELECT id, request_kind, received_at FROM customer_privacy_requests
                WHERE environment = ? AND verified_subject_id = ? AND request_kind = ?
                """, rs -> {
            if (!rs.next()) throw new IllegalStateException("Privacy request was not recorded");
            return new Request(rs.getLong(1), PrivacyRequestKind.valueOf(rs.getString(2)),
                    rs.getTimestamp(3).toInstant());
        }, environment.name(), subject, kind.name());
    }

    @Transactional(readOnly = true)
    public List<Request> forSubject(ConsentEnvironment environment, UUID subject) {
        requireIdentity(environment, subject);
        return jdbc.query("""
                SELECT id, request_kind, received_at FROM customer_privacy_requests
                WHERE environment = ? AND verified_subject_id = ? ORDER BY received_at DESC, id DESC
                """, (rs, row) -> new Request(rs.getLong(1), PrivacyRequestKind.valueOf(rs.getString(2)),
                rs.getTimestamp(3).toInstant()), environment.name(), subject);
    }

    private static void requireIdentity(ConsentEnvironment environment, UUID subject) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(subject);
    }

    public record Request(long id, PrivacyRequestKind kind, Instant receivedAt) { }
}
