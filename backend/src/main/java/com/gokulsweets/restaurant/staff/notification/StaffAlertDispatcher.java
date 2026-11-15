package com.gokulsweets.restaurant.staff.notification;

import com.gokulsweets.restaurant.customer.notification.WebPushTransport;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.UUID;

/** Backend staff alert dispatcher contract and implementation. */
@Component
@RequiredArgsConstructor
public class StaffAlertDispatcher {

    private static final int BATCH_SIZE = 10;

    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private final StaffOrderAlerts alerts;

    private final StaffAlertProperties properties;

    private final StaffAlertEmail email;

    private final WebPushTransport push;

    private final JdbcTemplate jdbc;

    private final TransactionTemplate transactions;

    /** Returns scheduled information for staff alert dispatcher. */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void scheduled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertDispatcher.class, "scheduled()");
        try {
            if (dedicatedImportWorker) return;
            if (!properties.isSchedulerEnabled() || !alerts.enabled()) return;
            alerts.generateReminders();
            dispatchBatch();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertDispatcher.class, "scheduled()");
        }
    }

    /** Dispatches batch. */
    public void dispatchBatch() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertDispatcher.class, "dispatchBatch()");
        try {
            if (!alerts.enabled()) return;
            transactions.executeWithoutResult(
                    status -> {
                        jdbc.update(
                                """
UPDATE staff_alert_deliveries SET state = CASE WHEN attempts >= 3 THEN 'FAILED' ELSE 'QUEUED' END,
    lease_token = NULL, lease_until = NULL WHERE state = 'SENDING' AND lease_until < CURRENT_TIMESTAMP
    AND event_id IN (SELECT id FROM staff_order_alerts WHERE environment = ?)
""",
                                alerts.scope());
                        if (push.configured())
                            jdbc.update(
                                    """
INSERT INTO staff_alert_deliveries(event_id, staff_id, channel, subscription_id)
SELECT e.id, u.id, 'PUSH', s.id FROM staff_order_alerts e
JOIN staff_push_subscriptions s ON s.environment = e.environment
JOIN staff_users u ON u.id = s.staff_id JOIN staff_sessions v ON v.token_hash = s.session_hash
WHERE e.environment = ? AND s.revoked_at IS NULL AND s.paused_at IS NULL AND v.revoked_at IS NULL
  AND v.expires_at > CURRENT_TIMESTAMP
  AND e.created_at >= s.subscribed_at AND e.created_at > CURRENT_TIMESTAMP - INTERVAL '5 minutes'
  AND (e.kind IN ('PREPARATION_DUE','PREPARATION_OVERDUE','READY_OVERDUE')
    OR NOT EXISTS (SELECT 1 FROM staff_order_alert_reads r WHERE r.event_id = e.id AND r.staff_id = u.id))
  AND
"""
                                            + StaffOrderAlerts.ELIGIBLE
                                            + " ON CONFLICT DO NOTHING",
                                    alerts.scope());
                        if (email.configured()) {
                            var recipients =
                                    jdbc.query(
                                            """
SELECT e.id, u.id staff_id FROM staff_order_alerts e JOIN staff_users u ON TRUE
WHERE e.environment = ? AND e.kind IN ('PREPARATION_OVERDUE','READY_OVERDUE')
  AND e.id = (SELECT MIN(first.id) FROM staff_order_alerts first
    WHERE first.environment=e.environment AND first.order_id=e.order_id
      AND first.kind=e.kind AND first.scheduled_at=e.scheduled_at)
  AND e.scheduled_at + (? * INTERVAL '1 minute') <= ? AND e.scheduled_at > CAST(? AS TIMESTAMP) - INTERVAL '1 day'
  AND
"""
                                                    + StaffOrderAlerts.ELIGIBLE,
                                            (rs, row) ->
                                                    new Recipient(rs.getLong(1), rs.getLong(2)),
                                            alerts.scope(),
                                            alerts.escalationMinutes(),
                                            Timestamp.valueOf(alerts.now()),
                                            Timestamp.valueOf(alerts.now()));
                            for (var recipient : recipients)
                                if (email.recipient(recipient.staffId()) != null)
                                    jdbc.update(
                                            "INSERT INTO staff_alert_deliveries(event_id, staff_id,"
                                                + " channel) VALUES (?, ?, 'EMAIL') ON CONFLICT DO"
                                                + " NOTHING",
                                            recipient.eventId(),
                                            recipient.staffId());
                        }
                    });
            // Each HTTP request is bounded to ten seconds; each claimed task has its own lease.
            for (int i = 0; i < BATCH_SIZE; i++) {
                boolean catchUpSlot = i == BATCH_SIZE - 1;
                Task task =
                        transactions.execute(
                                status -> {
                                    // Reserve the last slot for catch-up and lend it to live work
                                    // when there is no due catch-up.
                                    Task catchUp = catchUpSlot ? claim(true) : null;
                                    return catchUp != null ? catchUp : claim(false);
                                });
                if (task == null) return;
                if (!alerts.enabled()
                        || !alerts.eligible(task.event().id(), task.staffId())
                        || !alerts.actionable(task.event())) {
                    finish(task, "SKIPPED", null);
                    continue;
                }
                if (task.channel().equals("PUSH")) {
                    if (!canSendPush(task)) continue;
                } else if (email.recipient(task.staffId()) == null
                        || task.event().scheduledAt() == null
                        || alerts.now()
                                .isBefore(
                                        task.event()
                                                .scheduledAt()
                                                .plusMinutes(alerts.escalationMinutes()))) {
                    finish(task, "SKIPPED", null);
                    continue;
                }
                try {
                    String path = task.event().targetUrl();
                    int code =
                            task.channel().equals("PUSH")
                                    ? push.sendStaff(
                                            task.endpoint(),
                                            task.publicKey(),
                                            task.auth(),
                                            task.event().id(),
                                            task.event().title(),
                                            task.event().message(),
                                            path,
                                            task.subscriptionId())
                                    : email.send(
                                            task.staffId(),
                                            task.event().title(),
                                            task.event().message(),
                                            task.event().orderNumber(),
                                            task.event().id());
                    if (code >= 200 && code < 300) finish(task, "ACCEPTED", code);
                    else if (task.channel().equals("PUSH") && (code == 404 || code == 410)) {
                        transactions.executeWithoutResult(
                                status -> {
                                    jdbc.update(
                                            "UPDATE staff_push_subscriptions SET revoked_at ="
                                                + " COALESCE(revoked_at, CURRENT_TIMESTAMP) WHERE"
                                                + " id = ?",
                                            task.subscriptionId());
                                    finish(task, "REVOKED", code);
                                });
                    } else if (code == 429 || code >= 500) retry(task, code);
                    else finish(task, "FAILED", code);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    retry(task, null);
                    return;
                } catch (Exception unavailable) {
                    retry(task, null);
                }
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertDispatcher.class, "dispatchBatch()");
        }
    }

    /**
     * Checks the current device and serializes a skip with reconnect's delivery update.
     *
     * @param task the leased push delivery
     * @return whether the delivery is still eligible for transport
     */
    private boolean canSendPush(Task task) {
        final long started = MethodTiming.start(StaffAlertDispatcher.class, "canSendPush(Task)");
        try {
            return Boolean.TRUE.equals(
                    transactions.execute(
                            status -> {
                                var claimed =
                                        jdbc.query(
                                                """
SELECT catch_up FROM staff_alert_deliveries WHERE id=? AND state='SENDING' AND lease_token=? FOR UPDATE
""",
                                                (rs, row) -> rs.getBoolean(1),
                                                task.id(),
                                                task.lease());
                                if (claimed.isEmpty()) return false;
                                boolean catchUp = claimed.getFirst();
                                boolean live =
                                        push.configured()
                                                && Boolean.TRUE.equals(
                                                        jdbc.queryForObject(
                                                                """
SELECT EXISTS (SELECT 1 FROM staff_push_subscriptions s
JOIN staff_sessions v ON v.token_hash=s.session_hash
JOIN staff_users u ON u.id=s.staff_id
JOIN staff_order_alerts e ON e.id=?
WHERE s.id=? AND s.staff_id=? AND s.environment=?
  AND s.revoked_at IS NULL AND s.paused_at IS NULL AND v.revoked_at IS NULL
  AND v.expires_at > CURRENT_TIMESTAMP AND u.active
  AND (? OR (s.subscribed_at <= e.created_at AND e.created_at > CURRENT_TIMESTAMP - INTERVAL '5 minutes'
    AND (e.kind IN ('PREPARATION_DUE','PREPARATION_OVERDUE','READY_OVERDUE')
      OR NOT EXISTS (SELECT 1 FROM staff_order_alert_reads r WHERE r.event_id=e.id AND r.staff_id=u.id)))))
""",
                                                                Boolean.class,
                                                                task.event().id(),
                                                                task.subscriptionId(),
                                                                task.staffId(),
                                                                alerts.scope(),
                                                                catchUp));
                                if (!live) finish(task, "SKIPPED", null);
                                return live;
                            }));
        } finally {
            MethodTiming.finish(started, StaffAlertDispatcher.class, "canSendPush(Task)");
        }
    }

    /**
     * Claims staff alert dispatcher data and returns the {@code Task} result.
     *
     * <p>Reads {@code orders}, {@code staff_alert_deliveries}, {@code staff_order_alerts}, {@code
     * staff_push_subscriptions}.
     *
     * <p>Locks one due staff delivery row with {@code FOR UPDATE OF d SKIP LOCKED} before claiming
     * it.
     *
     * <p>Writes {@code staff_alert_deliveries}.
     *
     * @param catchUpOnly whether to claim only a catch-up delivery
     * @return the {@code Task} result
     */
    private Task claim(boolean catchUpOnly) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertDispatcher.class, "claim(boolean)");
        try {
            var tasks =
                    jdbc.query(
                            """
SELECT e.*, COALESCE(o.order_number,'Request '||LEFT(e.enquiry_id::text,8)) order_number, o.customer_order_number, d.id delivery_id, d.staff_id, d.channel, d.subscription_id, d.attempts,
  s.endpoint, s.public_key, s.auth_secret FROM staff_alert_deliveries d
JOIN staff_order_alerts e ON e.id = d.event_id LEFT JOIN orders o ON o.id = e.order_id
LEFT JOIN staff_push_subscriptions s ON s.id = d.subscription_id
WHERE e.environment = ? AND d.state = 'QUEUED' AND d.next_attempt_at <= CURRENT_TIMESTAMP
  AND (NOT ? OR d.catch_up)
ORDER BY d.catch_up, d.id LIMIT 1 FOR UPDATE OF d SKIP LOCKED
""",
                            (rs, row) ->
                                    new Task(
                                            rs.getLong("delivery_id"),
                                            rs.getLong("staff_id"),
                                            rs.getString("channel"),
                                            (UUID) rs.getObject("subscription_id"),
                                            rs.getInt("attempts") + 1,
                                            UUID.randomUUID(),
                                            StaffOrderAlerts.event(rs),
                                            rs.getString("endpoint"),
                                            rs.getString("public_key"),
                                            rs.getString("auth_secret")),
                            alerts.scope(),
                            catchUpOnly);
            if (tasks.isEmpty()) return null;
            var task = tasks.getFirst();
            jdbc.update(
                    "UPDATE staff_alert_deliveries SET state = 'SENDING', attempts = ?, lease_token"
                        + " = ?, lease_until = CURRENT_TIMESTAMP + INTERVAL '30 seconds' WHERE id ="
                        + " ?",
                    task.attempts(),
                    task.lease(),
                    task.id());
            return task;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertDispatcher.class, "claim(boolean)");
        }
    }

    /**
     * Finishes staff alert dispatcher data.
     *
     * <p>Writes {@code staff_alert_deliveries}.
     *
     * @param task the task supplied to this method
     * @param state the state supplied to this method
     * @param code the code supplied to this method
     */
    private void finish(Task task, String state, Integer code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertDispatcher.class, "finish(Task,String,Integer)");
        try {
            jdbc.update(
                    """
UPDATE staff_alert_deliveries SET state = ?,
  attempts = CASE WHEN ? THEN GREATEST(0, attempts - 1) ELSE attempts END,
  last_http_status = CASE WHEN ? THEN last_http_status ELSE ? END,
  accepted_at = CASE WHEN ? = 'ACCEPTED' THEN CURRENT_TIMESTAMP ELSE NULL END,
  lease_token = NULL, lease_until = NULL WHERE id = ? AND state = 'SENDING' AND lease_token = ?
""",
                    state,
                    "SKIPPED".equals(state),
                    "SKIPPED".equals(state),
                    code,
                    state,
                    task.id(),
                    task.lease());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffAlertDispatcher.class,
                    "finish(Task,String,Integer)");
        }
    }

    /**
     * Retries staff alert dispatcher data.
     *
     * <p>Writes {@code staff_alert_deliveries}.
     *
     * @param task the task supplied to this method
     * @param code the code supplied to this method
     */
    private void retry(Task task, Integer code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffAlertDispatcher.class, "retry(Task,Integer)");
        try {
            if (task.attempts() >= 3) {
                finish(task, "FAILED", code);
                return;
            }
            jdbc.update(
                    """
UPDATE staff_alert_deliveries SET state = 'QUEUED', last_http_status = ?, next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '60 seconds',
  lease_token = NULL, lease_until = NULL WHERE id = ? AND state = 'SENDING' AND lease_token = ?
""",
                    code,
                    task.id(),
                    task.lease());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffAlertDispatcher.class, "retry(Task,Integer)");
        }
    }

    /**
     * Immutable recipient data contract.
     *
     * @param eventId the event id
     * @param staffId the staff id
     */
    private record Recipient(long eventId, long staffId) {}

    /**
     * Immutable task data contract.
     *
     * @param id the id
     * @param staffId the staff id
     * @param channel the channel
     * @param subscriptionId the subscription id
     * @param attempts the attempts
     * @param lease the lease
     * @param event the event
     * @param endpoint the endpoint
     * @param publicKey the public key
     * @param auth the auth
     */
    private record Task(
            long id,
            long staffId,
            String channel,
            UUID subscriptionId,
            int attempts,
            UUID lease,
            StaffOrderAlerts.Event event,
            String endpoint,
            String publicKey,
            String auth) {}
}
