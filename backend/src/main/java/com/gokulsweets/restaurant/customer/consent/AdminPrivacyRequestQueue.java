package com.gokulsweets.restaurant.customer.consent;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owner-only request metadata queue. Fulfillment requires separate legal approval. */
@Service
@RequiredArgsConstructor
public class AdminPrivacyRequestQueue {

    private final JdbcTemplate jdbc;

    private final EnhancementProperties features;

    private final Environment settings;

    private final StaffAuthorizationService staff;

    /**
     * Returns view information for admin privacy request queue.
     *
     * <p>Authorization checks include {@code PermissionName.PRIVACY_REQUEST_VIEW}.
     *
     * <p>Reads {@code customer_privacy_requests}.
     *
     * <p>Writes {@code customer_privacy_queue_access_audit}.
     *
     * @param page the page supplied to this method
     * @return the value of {@code entries}
     */
    @Transactional
    public List<Entry> view(int page) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPrivacyRequestQueue.class, "view(int)");
        try {
            if (!features.isCustomerConsentControls() || page < 0 || page > 1000) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            var environment = environment();
            staff.requirePermission(PermissionName.PRIVACY_REQUEST_VIEW);
            var actor = staff.getCurrentStaff();
            var entries =
                    jdbc.query(
                            """
                            SELECT id, verified_subject_id, request_kind, received_at, review_state
                            FROM customer_privacy_requests WHERE environment = ?
                            ORDER BY received_at DESC, id DESC LIMIT 50 OFFSET ?
                            """,
                            (rs, row) -> entry(rs),
                            environment.name(),
                            page * 50);
            jdbc.update(
                    """
                    INSERT INTO customer_privacy_queue_access_audit
                    (staff_user_id, environment, returned_count, page_number) VALUES (?, ?, ?, ?)
                    """,
                    actor.getId(),
                    environment.name(),
                    entries.size(),
                    page);
            return entries;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPrivacyRequestQueue.class, "view(int)");
        }
    }

    /**
     * Triages admin privacy request queue data and returns the {@code Entry} result.
     *
     * <p>Authorization checks include {@code PermissionName.PRIVACY_REQUEST_VIEW}.
     *
     * <p>Reads {@code customer_privacy_requests}.
     *
     * <p>Writes {@code customer_privacy_requests}, {@code customer_privacy_triage_events}.
     *
     * @param requestId the request id supplied to this method
     * @param state the state supplied to this method
     * @return the {@code Entry} result
     */
    @Transactional
    public Entry triage(long requestId, PrivacyReviewState state) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPrivacyRequestQueue.class, "triage(long,PrivacyReviewState)");
        try {
            if (!features.isCustomerConsentControls())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (requestId <= 0 || state == null || state == PrivacyReviewState.RECEIVED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            var environment = environment();
            staff.requirePermission(PermissionName.PRIVACY_REQUEST_VIEW);
            var actor = staff.getCurrentStaff();
            // Lock the request so two staff decisions cannot reorder their audit events.
            var prior =
                    jdbc.query(
                            """
SELECT id, verified_subject_id, request_kind, received_at, review_state
FROM customer_privacy_requests WHERE id = ? AND environment = ? FOR UPDATE
""",
                            rs -> rs.next() ? entry(rs) : null,
                            requestId,
                            environment.name());
            if (prior == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (prior.state() == state) return prior;
            jdbc.update(
                    "UPDATE customer_privacy_requests SET review_state = ? WHERE id = ? AND"
                            + " environment = ?",
                    state.name(),
                    requestId,
                    environment.name());
            jdbc.update(
                    """
INSERT INTO customer_privacy_triage_events (request_id, staff_user_id, from_state, to_state)
VALUES (?, ?, ?, ?)
""",
                    requestId,
                    actor.getId(),
                    prior.state().name(),
                    state.name());
            return new Entry(
                    prior.id(), prior.subjectId(), prior.kind(), prior.receivedAt(), state);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrivacyRequestQueue.class,
                    "triage(long,PrivacyReviewState)");
        }
    }

    /**
     * Returns environment information for admin privacy request queue.
     *
     * @return the {@code ConsentEnvironment} result
     */
    private ConsentEnvironment environment() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPrivacyRequestQueue.class, "environment()");
        try {
            return switch (settings.getProperty("gokul.environment-isolation.environment", "")) {
                case "DEV" -> ConsentEnvironment.DEV;
                case "PROD" -> ConsentEnvironment.PROD;
                default -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
            };
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPrivacyRequestQueue.class, "environment()");
        }
    }

    /**
     * Returns entry information for admin privacy request queue.
     *
     * @param rs the rs supplied to this method
     * @return the {@code Entry} result
     * @throws java.sql.SQLException if the underlying operation fails
     */
    private static Entry entry(java.sql.ResultSet rs) throws java.sql.SQLException {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPrivacyRequestQueue.class, "entry(java.sql.ResultSet)");
        try {
            return new Entry(
                    rs.getLong(1),
                    (UUID) rs.getObject(2),
                    PrivacyRequestKind.valueOf(rs.getString(3)),
                    rs.getTimestamp(4).toInstant(),
                    PrivacyReviewState.valueOf(rs.getString(5)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrivacyRequestQueue.class,
                    "entry(java.sql.ResultSet)");
        }
    }

    /**
     * Immutable entry data contract.
     *
     * @param id the id
     * @param subjectId the subject id
     * @param kind the kind
     * @param receivedAt the received at
     * @param state the state
     */
    public record Entry(
            long id,
            UUID subjectId,
            PrivacyRequestKind kind,
            Instant receivedAt,
            PrivacyReviewState state) {}
}
