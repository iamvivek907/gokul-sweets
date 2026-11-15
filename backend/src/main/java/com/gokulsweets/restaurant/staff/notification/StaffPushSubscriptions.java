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
     * @param registrationId the existing device id when restoring an opt-in
     * @param expectedStaffId the account that started this request
     * @param pausePrevious whether to deactivate another account on this device without opting in
     */
    public record Input(
            String endpoint,
            String publicKey,
            String authSecret,
            UUID registrationId,
            Long expectedStaffId,
            boolean pausePrevious) {
        public Input(String endpoint, String publicKey, String authSecret) {
            this(endpoint, publicKey, authSecret, null);
        }

        public Input(String endpoint, String publicKey, String authSecret, UUID registrationId) {
            this(endpoint, publicKey, authSecret, registrationId, null, false);
        }
    }

    /**
     * Immutable result data contract.
     *
     * @param id the id
     * @param created whether this request created the registration
     */
    public record Result(UUID id, boolean created) {}

    /**
     * Subscribes to staff push subscriptions data and returns the {@code Result} result.
     *
     * <p>Reads {@code staff_push_subscriptions}, {@code staff_sessions}, {@code staff_users}.
     *
     * <p>Locks the active staff row and matching subscription row before registering or replacing
     * the endpoint.
     *
     * <p>Writes {@code staff_push_subscriptions}.
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
            if (input.expectedStaffId() != null && input.expectedStaffId() != staffId)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "The active account changed.");
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
  s.revoked_at IS NOT NULL OR v.revoked_at IS NOT NULL OR NOT u.active inactive, s.paused_at IS NOT NULL
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
                                            rs.getBoolean(6),
                                            rs.getBoolean(7)),
                            alerts.scope(),
                            hash);
            var old = existing.isEmpty() ? null : existing.getFirst();
            boolean differentOwner = old != null && !(old.staffId() == staffId);
            boolean sameDevice =
                    old != null
                            && old.publicKey().equals(input.publicKey())
                            && old.auth().equals(input.authSecret());
            if (input.pausePrevious()) {
                if (differentOwner && sameDevice && !old.inactive()) {
                    jdbc.update(
                            "UPDATE staff_push_subscriptions SET revoked_at=CURRENT_TIMESTAMP WHERE"
                                    + " id=?",
                            old.id());
                }
                return new Result(null, false);
            }
            Long count =
                    jdbc.queryForObject(
                            """
SELECT COUNT(*) FROM staff_push_subscriptions s JOIN staff_sessions v ON v.token_hash = s.session_hash
JOIN staff_users u ON u.id = s.staff_id WHERE s.environment = ? AND s.staff_id = ? AND s.endpoint_hash <> ?
  AND s.revoked_at IS NULL AND s.paused_at IS NULL AND v.revoked_at IS NULL
  AND v.expires_at > CURRENT_TIMESTAMP AND u.active
""",
                            Long.class,
                            alerts.scope(),
                            staffId,
                            hash);
            if (count != null && count >= 5)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Maximum five live staff browsers.");
            if (input.registrationId() != null) {
                // Existing-device restoration must never recreate a removed subscription.
                var activeSession =
                        jdbc.query(
                                """
SELECT token_hash FROM staff_sessions WHERE token_hash=? AND staff_id=?
  AND revoked_at IS NULL AND expires_at>CURRENT_TIMESTAMP FOR SHARE
""",
                                (rs, row) -> rs.getString(1),
                                session,
                                staffId);
                if (activeSession.isEmpty())
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
                var restored =
                        jdbc.query(
                                """
UPDATE staff_push_subscriptions s SET session_hash=?,
    subscribed_at=CASE WHEN s.paused_at IS NULL THEN s.subscribed_at ELSE CURRENT_TIMESTAMP END,
    paused_at=NULL
FROM staff_sessions old, staff_users u
WHERE s.id=? AND s.environment=? AND s.staff_id=? AND s.endpoint_hash=?
  AND s.public_key=? AND s.auth_secret=? AND s.revoked_at IS NULL
  AND old.token_hash=s.session_hash AND u.id=s.staff_id AND u.active
RETURNING s.id
""",
                                (rs, row) -> rs.getObject(1, UUID.class),
                                session,
                                input.registrationId(),
                                alerts.scope(),
                                staffId,
                                hash,
                                input.publicKey(),
                                input.authSecret());
                if (restored.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                if (old != null && old.paused()) queuePendingOrders(staffId, restored.getFirst());
                return new Result(restored.getFirst(), false);
            }
            if (differentOwner && !old.inactive() && !sameDevice)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This browser belongs to another staff session. Disable its old"
                                + " registration first.");
            if (old != null) {
                if (!old.inactive()
                        && old.staffId() == staffId
                        && old.session().equals(session)
                        && old.publicKey().equals(input.publicKey())
                        && old.auth().equals(input.authSecret()))
                    return new Result(old.id(), false);
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
            return new Result(id, true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPushSubscriptions.class,
                    "subscribe(long,String,Input)");
        }
    }

    /**
     * Queues never-delivered new-order alerts for orders still waiting for staff action.
     *
     * @param staffId the authenticated staff account
     * @param deviceId the resumed registration
     */
    private void queuePendingOrders(long staffId, UUID deviceId) {
        final long started =
                MethodTiming.start(StaffPushSubscriptions.class, "queuePendingOrders(long,UUID)");
        try {
            jdbc.update(
                    """
INSERT INTO staff_alert_deliveries AS d(event_id, staff_id, channel, subscription_id, catch_up)
SELECT e.id, u.id, 'PUSH', ?, TRUE
FROM orders o JOIN staff_order_alerts e ON e.order_id=o.id AND e.branch_id=o.branch_id
JOIN staff_users u ON u.id=?
WHERE o.order_status='CONFIRMED' AND e.environment=? AND e.kind='NEW_ORDER' AND
"""
                            + StaffOrderAlerts.ELIGIBLE
                            + """
ON CONFLICT (event_id, subscription_id) WHERE channel='PUSH' DO UPDATE
SET catch_up=TRUE,
    state=CASE WHEN d.state IN ('SKIPPED','FAILED') THEN 'QUEUED' ELSE d.state END,
    attempts=CASE WHEN d.state='FAILED' THEN 2
                  WHEN d.state='SKIPPED' THEN LEAST(d.attempts, 2) ELSE d.attempts END,
    next_attempt_at=CASE WHEN d.state IN ('SKIPPED','FAILED') THEN CURRENT_TIMESTAMP ELSE d.next_attempt_at END,
    reconnect_retry_used=d.reconnect_retry_used OR d.state='FAILED'
WHERE d.state IN ('QUEUED','SENDING','SKIPPED')
   OR (d.state='FAILED' AND d.attempts=3 AND NOT d.reconnect_retry_used
       AND (d.last_http_status IS NULL OR d.last_http_status=429 OR d.last_http_status>=500))
""",
                    deviceId,
                    staffId,
                    alerts.scope());
        } finally {
            MethodTiming.finish(
                    started, StaffPushSubscriptions.class, "queuePendingOrders(long,UUID)");
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
  AND s.revoked_at IS NULL AND s.paused_at IS NULL AND v.revoked_at IS NULL AND v.expires_at > CURRENT_TIMESTAMP
  AND u.active)
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
     * @param paused whether the device was paused on sign-out
     */
    private record Existing(
            UUID id,
            long staffId,
            String session,
            String publicKey,
            String auth,
            boolean inactive,
            boolean paused) {}
}
