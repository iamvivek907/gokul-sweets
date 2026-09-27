package com.gokulsweets.restaurant.customer.consent;

import com.gokulsweets.restaurant.config.EnhancementProperties;
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

    @Transactional
    public List<Entry> view(int page) {
        if (!features.isCustomerConsentControls() || page < 0 || page > 1000) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var environment = environment();
        staff.requirePermission(PermissionName.PRIVACY_REQUEST_VIEW);
        var actor = staff.getCurrentStaff();
        var entries = jdbc.query("""
                SELECT id, verified_subject_id, request_kind, received_at, review_state
                FROM customer_privacy_requests WHERE environment = ?
                ORDER BY received_at DESC, id DESC LIMIT 50 OFFSET ?
                """, (rs, row) -> entry(rs), environment.name(), page * 50);
        jdbc.update("""
                INSERT INTO customer_privacy_queue_access_audit
                (staff_user_id, environment, returned_count, page_number) VALUES (?, ?, ?, ?)
                """, actor.getId(), environment.name(), entries.size(), page);
        return entries;
    }

    @Transactional
    public Entry triage(long requestId, PrivacyReviewState state) {
        if (!features.isCustomerConsentControls()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (requestId <= 0 || state == null || state == PrivacyReviewState.RECEIVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        var environment = environment();
        staff.requirePermission(PermissionName.PRIVACY_REQUEST_VIEW);
        var actor = staff.getCurrentStaff();
        // Lock the request so two staff decisions cannot reorder their audit events.
        var prior = jdbc.query("""
                SELECT id, verified_subject_id, request_kind, received_at, review_state
                FROM customer_privacy_requests WHERE id = ? AND environment = ? FOR UPDATE
                """, rs -> rs.next() ? entry(rs) : null, requestId, environment.name());
        if (prior == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (prior.state() == state) return prior;
        jdbc.update("UPDATE customer_privacy_requests SET review_state = ? WHERE id = ? AND environment = ?",
                state.name(), requestId, environment.name());
        jdbc.update("""
                INSERT INTO customer_privacy_triage_events (request_id, staff_user_id, from_state, to_state)
                VALUES (?, ?, ?, ?)
                """, requestId, actor.getId(), prior.state().name(), state.name());
        return new Entry(prior.id(), prior.subjectId(), prior.kind(), prior.receivedAt(), state);
    }

    private ConsentEnvironment environment() {
        return switch (settings.getProperty("gokul.environment-isolation.environment", "")) {
            case "DEV" -> ConsentEnvironment.DEV;
            case "PROD" -> ConsentEnvironment.PROD;
            default -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        };
    }

    private static Entry entry(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Entry(rs.getLong(1), (UUID) rs.getObject(2),
                PrivacyRequestKind.valueOf(rs.getString(3)), rs.getTimestamp(4).toInstant(),
                PrivacyReviewState.valueOf(rs.getString(5)));
    }

    public record Entry(long id, UUID subjectId, PrivacyRequestKind kind, Instant receivedAt,
                        PrivacyReviewState state) { }
}
