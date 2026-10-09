package com.gokulsweets.restaurant.staff.notification;

import com.gokulsweets.restaurant.customer.notification.WebPushTransport;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffSessionService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Backend staff push subscriptions contract and implementation. */
@Service
@RequiredArgsConstructor
public class StaffPushSubscriptions {

    private final JdbcTemplate jdbc;

    private final StaffSessionService sessions;

    private final StaffOrderAlerts alerts;

    private final WebPushTransport push;

    /**
     * Immutable input data contract.
     *
     * @param endpoint the endpoint
     * @param publicKey the public key
     * @param authSecret the auth secret
     */
    public record Input(String endpoint, String publicKey, String authSecret) {}

    /**
     * Immutable result data contract.
     *
     * @param id the id
     */
    public record Result(UUID id) {}

    /**
     * Subscribes to staff push subscriptions data and returns the {@code Result} result.
     *
     * <p>Reads {@code staff_push_subscriptions}, {@code staff_sessions}, {@code staff_users}.
     *
     * <p>Writes {@code OF}, {@code staff_push_subscriptions}.
     *
     * <p>Delegates to {@code StaffSessionService.hash(...)}.
     *
     * @param staffId the staff id supplied to this method
     * @param cookie the cookie supplied to this method
     * @param input the input supplied to this method
     * @return the {@code Result} result
     * @throws ResponseStatusException when the method rejects the request with {@code Maximum five
     *     live staff browsers.}; {@code Push is not configured; use the preparation queue.}; {@code
     *     This browser belongs to another staff session. Disable its old registration first.}
     */
    @Transactional
    public Result subscribe(long staffId, String cookie, Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPushSubscriptions.class, "subscribe(long,String,Input)");
        try {
            if (!alerts.enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (!push.configured())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Push is not configured; use the preparation queue.");
            if (input == null
                    || !WebPushTransport.validEndpoint(input.endpoint())
                    || !WebPushTransport.validRecipientKeys(input.publicKey(), input.authSecret()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            var verified = sessions.verify(cookie);
            if (verified == null || verified.staffId() != staffId)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            jdbc.queryForObject(
                    "SELECT id FROM staff_users WHERE id = ? AND active FOR UPDATE",
                    Long.class,
                    staffId);
            String session = StaffSessionService.hash(cookie),
                    hash = StaffSessionService.hash(input.endpoint());
            var existing =
                    jdbc.query(
                            """
SELECT s.id, s.staff_id, s.session_hash, s.public_key, s.auth_secret,
  s.revoked_at IS NOT NULL OR v.revoked_at IS NOT NULL OR v.expires_at <= CURRENT_TIMESTAMP OR v.staff_updated_at <> u.updated_at OR NOT u.active inactive
FROM staff_push_subscriptions s JOIN staff_sessions v ON v.token_hash = s.session_hash
JOIN staff_users u ON u.id = s.staff_id WHERE s.environment = ? AND s.endpoint_hash = ? FOR UPDATE OF s
""",
                            (rs, row) ->
                                    new Existing(
                                            (UUID) rs.getObject(1),
                                            rs.getLong(2),
                                            rs.getString(3),
                                            rs.getString(4),
                                            rs.getString(5),
                                            rs.getBoolean(6)),
                            alerts.scope(),
                            hash);
            if (!existing.isEmpty()
                    && !existing.getFirst().inactive()
                    && existing.getFirst().staffId() != staffId)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This browser belongs to another staff session. Disable its old"
                                + " registration first.");
            Long count =
                    jdbc.queryForObject(
                            """
SELECT COUNT(*) FROM staff_push_subscriptions s JOIN staff_sessions v ON v.token_hash = s.session_hash
JOIN staff_users u ON u.id = s.staff_id WHERE s.environment = ? AND s.staff_id = ? AND s.endpoint_hash <> ?
  AND s.revoked_at IS NULL AND v.revoked_at IS NULL AND v.expires_at > CURRENT_TIMESTAMP AND v.staff_updated_at = u.updated_at AND u.active
""",
                            Long.class,
                            alerts.scope(),
                            staffId,
                            hash);
            if (count != null && count >= 5)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Maximum five live staff browsers.");
            if (!existing.isEmpty()) {
                var old = existing.getFirst();
                if (!old.inactive()
                        && old.staffId() == staffId
                        && old.session().equals(session)
                        && old.publicKey().equals(input.publicKey())
                        && old.auth().equals(input.authSecret())) return new Result(old.id());
                jdbc.update(
                        "UPDATE staff_push_subscriptions SET revoked_at = CURRENT_TIMESTAMP,"
                                + " endpoint_hash = ? WHERE id = ?",
                        "retired:" + old.id(),
                        old.id());
            }
            UUID id = UUID.randomUUID();
            jdbc.update(
                    """
INSERT INTO staff_push_subscriptions(id, environment, staff_id, session_hash, endpoint, endpoint_hash, public_key, auth_secret)
VALUES (?, ?, ?, ?, ?, ?, ?, ?)
""",
                    id,
                    alerts.scope(),
                    staffId,
                    session,
                    input.endpoint(),
                    hash,
                    input.publicKey(),
                    input.authSecret());
            return new Result(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPushSubscriptions.class,
                    "subscribe(long,String,Input)");
        }
    }

    /**
     * Revokes staff push subscriptions data.
     *
     * <p>Writes {@code staff_push_subscriptions}.
     *
     * @param staffId the staff id supplied to this method
     * @param id the id supplied to this method
     */
    @Transactional
    public void revoke(long staffId, UUID id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPushSubscriptions.class, "revoke(long,UUID)");
        try {
            if (jdbc.update(
                            "UPDATE staff_push_subscriptions SET revoked_at = COALESCE(revoked_at,"
                                    + " CURRENT_TIMESTAMP) WHERE id = ? AND environment = ? AND"
                                    + " staff_id = ?",
                            id,
                            alerts.scope(),
                            staffId)
                    == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffPushSubscriptions.class, "revoke(long,UUID)");
        }
    }

    /**
     * Returns live information for staff push subscriptions.
     *
     * <p>Reads {@code staff_push_subscriptions}, {@code staff_sessions}, {@code staff_users}.
     *
     * <p>Delegates to {@code StaffSessionService.hash(...)}.
     *
     * @param staffId the staff id supplied to this method
     * @param id the id supplied to this method
     * @param cookie the cookie supplied to this method
     * @return the {@code boolean} result
     */
    public boolean live(long staffId, UUID id, String cookie) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPushSubscriptions.class, "live(long,UUID,String)");
        try {
            return id != null
                    && cookie != null
                    && Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    """
SELECT EXISTS (SELECT 1 FROM staff_push_subscriptions s JOIN staff_sessions v ON v.token_hash = s.session_hash
JOIN staff_users u ON u.id = s.staff_id WHERE s.id = ? AND s.environment = ? AND s.staff_id = ? AND s.session_hash = ?
  AND s.revoked_at IS NULL AND v.revoked_at IS NULL AND v.expires_at > CURRENT_TIMESTAMP AND v.staff_updated_at = u.updated_at AND u.active)
""",
                                    Boolean.class,
                                    id,
                                    alerts.scope(),
                                    staffId,
                                    StaffSessionService.hash(cookie)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPushSubscriptions.class,
                    "live(long,UUID,String)");
        }
    }

    /**
     * Immutable existing data contract.
     *
     * @param id the id
     * @param staffId the staff id
     * @param session the session
     * @param publicKey the public key
     * @param auth the auth
     * @param inactive the inactive
     */
    private record Existing(
            UUID id,
            long staffId,
            String session,
            String publicKey,
            String auth,
            boolean inactive) {}
}
