package com.gokulsweets.restaurant.customer.notification;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Bounded leased delivery after the event's transaction commits. Never sends marketing. */
@Component
@RequiredArgsConstructor
public class CustomerPushDispatcher {

    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private final JdbcTemplate jdbc;

    private final CustomerAlertPreferences alerts;

    private final WebPushTransport transport;

    private final WebPushProperties properties;

    private final Environment settings;

    private final TransactionTemplate transactions;

    private final Clock inventoryClock;

    /** Returns scheduled information for customer push dispatcher. */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void scheduled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerPushDispatcher.class, "scheduled()");
        try {
            if (dedicatedImportWorker) return;
            if (properties.isSchedulerEnabled()) dispatchBatch();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerPushDispatcher.class, "scheduled()");
        }
    }

    /** Dispatches batch. */
    public void dispatchBatch() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerPushDispatcher.class, "dispatchBatch()");
        try {
            if (!alerts.enabled() || !transport.configured()) return;
            String environment =
                    settings.getProperty("gokul.environment-isolation.environment", "");
            transactions.executeWithoutResult(
                    status -> {
                        // A recovered worker retries with the same event ID; the SW's notification
                        // tag suppresses replacement alerts.
                        jdbc.update(
                                "UPDATE customer_push_deliveries SET state = CASE WHEN attempts >="
                                    + " 3 THEN 'FAILED' ELSE 'QUEUED' END, lease_token = NULL,"
                                    + " lease_until = NULL WHERE state = 'SENDING' AND lease_until"
                                    + " < CURRENT_TIMESTAMP AND subscription_id IN (SELECT id FROM"
                                    + " customer_push_subscriptions WHERE environment = ?)",
                                environment);
                        jdbc.update(
                                """
INSERT INTO customer_push_deliveries(event_id, subscription_id)
SELECT e.id, s.id FROM customer_notification_events e
JOIN customer_push_subscriptions s ON s.environment = e.environment AND s.subject_id = e.subject_id
JOIN verified_customer_sessions v ON v.id = s.session_id
WHERE e.environment = ? AND (e.read_at IS NULL OR (e.auto_acknowledged AND e.kind IN ('PICKED_UP','DELIVERED'))) AND s.revoked_at IS NULL
  AND v.environment = e.environment AND v.verified_subject_id = e.subject_id
  AND v.revoked_at IS NULL AND v.expires_at > CURRENT_TIMESTAMP
  AND e.created_at >= s.subscribed_at AND e.created_at > CURRENT_TIMESTAMP - INTERVAL '5 minutes'
ON CONFLICT (event_id, subscription_id) DO NOTHING
""",
                                environment);
                    });
            for (int i = 0; i < 3; i++) {
                Task task = transactions.execute(status -> claim(environment));
                if (task == null) return;
                if (!alerts.enabled()) {
                    finish(task, "SKIPPED", null);
                    continue;
                }
                // Recheck revocation/preferences immediately before a network attempt.
                Boolean live =
                        jdbc.queryForObject(
                                """
SELECT EXISTS (SELECT 1 FROM customer_push_subscriptions s
JOIN verified_customer_sessions v ON v.id = s.session_id
JOIN customer_notification_events e ON e.id = ?
WHERE s.id = ? AND s.revoked_at IS NULL AND v.revoked_at IS NULL
  AND v.expires_at > CURRENT_TIMESTAMP
  AND (e.read_at IS NULL OR (e.auto_acknowledged AND e.kind IN ('PICKED_UP','DELIVERED')))
  AND NOT EXISTS (SELECT 1 FROM customer_notification_events newer WHERE newer.environment=e.environment
    AND newer.subject_id=e.subject_id AND newer.target_type=e.target_type AND newer.target_id=e.target_id AND newer.id>e.id)
  AND (e.kind NOT IN ('PICKED_UP','DELIVERED') OR NOT EXISTS (SELECT 1 FROM reviews r
    JOIN orders reviewed ON reviewed.id=r.order_id WHERE reviewed.order_number=e.target_id))
  AND (e.kind NOT IN ('CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY',
    'OUT_FOR_DELIVERY','PICKED_UP','DELIVERED','CANCELLED','PICKUP_WINDOW_EXPIRED','NO_SHOW')
    OR EXISTS (SELECT 1 FROM orders o JOIN verified_order_ownership own ON own.order_id = o.id
      WHERE o.order_number = e.target_id AND o.order_status = e.kind
        AND own.environment = e.environment AND own.verified_subject_id = e.subject_id)))
""",
                                Boolean.class,
                                task.eventId(),
                                task.subscriptionId());
                var preference = alerts.settings(environment, task.subject());
                if (!Boolean.TRUE.equals(live)
                        || CustomerAlertPreferences.quiet(
                                preference.quietHoursEnabled(),
                                preference.quietStartMinute(),
                                preference.quietEndMinute(),
                                inventoryClock.instant())
                        || task.createdAt().isBefore(inventoryClock.instant().minusSeconds(300))) {
                    finish(task, "SKIPPED", null);
                    continue;
                }
                try {
                    int code =
                            transport.send(
                                    task.endpoint(),
                                    task.publicKey(),
                                    task.auth(),
                                    task.eventId(),
                                    task.title(),
                                    task.message(),
                                    task.url());
                    if (code >= 200 && code < 300) finish(task, "ACCEPTED", code);
                    else if (code == 404 || code == 410) {
                        transactions.executeWithoutResult(
                                status -> {
                                    jdbc.update(
                                            "UPDATE customer_push_subscriptions SET revoked_at ="
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
                    __gokulMethodStartedNanos, CustomerPushDispatcher.class, "dispatchBatch()");
        }
    }

    /**
     * Claims customer push dispatcher data and returns the {@code Task} result.
     *
     * <p>Reads {@code customer_notification_events}, {@code customer_push_deliveries}, {@code
     * customer_push_subscriptions}.
     *
     * <p>Locks one due delivery row with {@code FOR UPDATE OF d SKIP LOCKED} before claiming it.
     *
     * <p>Writes {@code customer_push_deliveries}.
     *
     * @param environment the environment supplied to this method
     * @return the {@code Task} result
     */
    private Task claim(String environment) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerPushDispatcher.class, "claim(String)");
        try {
            var tasks =
                    jdbc.query(
                            """
SELECT d.id, d.event_id, d.subscription_id, d.attempts, s.subject_id, s.endpoint,
    s.public_key, s.auth_secret, e.created_at, e.title, e.message, e.target_type, e.target_id, e.kind
FROM customer_push_deliveries d JOIN customer_push_subscriptions s ON s.id = d.subscription_id
JOIN customer_notification_events e ON e.id = d.event_id
WHERE d.state = 'QUEUED' AND d.next_attempt_at <= CURRENT_TIMESTAMP AND s.environment = ?
ORDER BY d.id LIMIT 1 FOR UPDATE OF d SKIP LOCKED
""",
                            (rs, row) ->
                                    new Task(
                                            rs.getLong(1),
                                            rs.getLong(2),
                                            (UUID) rs.getObject(3),
                                            rs.getInt(4) + 1,
                                            (UUID) rs.getObject(5),
                                            rs.getString(6),
                                            rs.getString(7),
                                            rs.getString(8),
                                            rs.getTimestamp(9).toInstant(),
                                            rs.getString(10),
                                            rs.getString(11),
                                            destination(rs.getString(12), rs.getString(13))
                                                    + (java.util.Set.of("PICKED_UP", "DELIVERED")
                                                                    .contains(rs.getString(14))
                                                            ? "#order-review"
                                                            : ""),
                                            UUID.randomUUID()),
                            environment);
            if (tasks.isEmpty()) return null;
            var task = tasks.getFirst();
            jdbc.update(
                    "UPDATE customer_push_deliveries SET state = 'SENDING', attempts = ?,"
                            + " lease_token = ?, lease_until = CURRENT_TIMESTAMP + INTERVAL '30"
                            + " seconds' WHERE id = ?",
                    task.attempts(),
                    task.lease(),
                    task.id());
            return task;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerPushDispatcher.class, "claim(String)");
        }
    }

    /**
     * Finishes customer push dispatcher data.
     *
     * <p>Writes {@code customer_push_deliveries}.
     *
     * @param task the task supplied to this method
     * @param state the state supplied to this method
     * @param code the code supplied to this method
     */
    private void finish(Task task, String state, Integer code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerPushDispatcher.class, "finish(Task,String,Integer)");
        try {
            jdbc.update(
                    """
UPDATE customer_push_deliveries SET state = ?, last_http_status = ?,
    accepted_at = CASE WHEN ? = 'ACCEPTED' THEN CURRENT_TIMESTAMP ELSE NULL END,
    lease_token = NULL, lease_until = NULL WHERE id = ? AND state = 'SENDING' AND lease_token = ?
""",
                    state,
                    code,
                    state,
                    task.id(),
                    task.lease());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerPushDispatcher.class,
                    "finish(Task,String,Integer)");
        }
    }

    /**
     * Retries customer push dispatcher data.
     *
     * <p>Writes {@code customer_push_deliveries}.
     *
     * @param task the task supplied to this method
     * @param code the code supplied to this method
     */
    private void retry(Task task, Integer code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerPushDispatcher.class, "retry(Task,Integer)");
        try {
            if (task.attempts() >= 3) {
                finish(task, "FAILED", code);
                return;
            }
            jdbc.update(
                    """
UPDATE customer_push_deliveries SET state = 'QUEUED', last_http_status = ?,
    next_attempt_at = CURRENT_TIMESTAMP + INTERVAL '60 seconds', lease_token = NULL, lease_until = NULL
WHERE id = ? AND state = 'SENDING' AND lease_token = ?
""",
                    code,
                    task.id(),
                    task.lease());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerPushDispatcher.class, "retry(Task,Integer)");
        }
    }

    /**
     * Returns destination information for customer push dispatcher.
     *
     * @param type the type supplied to this method
     * @param id the id supplied to this method
     * @return the {@code String} result
     */
    private static String destination(String type, String id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerPushDispatcher.class, "destination(String,String)");
        try {
            String encoded =
                    java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8)
                            .replace("+", "%20");
            return "ORDER".equals(type)
                    ? "/orders/" + encoded
                    : "/occasions/requests?enquiry=" + encoded;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerPushDispatcher.class,
                    "destination(String,String)");
        }
    }

    /**
     * Immutable task data contract.
     *
     * @param id the id
     * @param eventId the event id
     * @param subscriptionId the subscription id
     * @param attempts the attempts
     * @param subject the subject
     * @param endpoint the endpoint
     * @param publicKey the public key
     * @param auth the auth
     * @param createdAt the created at
     * @param title the title
     * @param message the message
     * @param url the url
     * @param lease the lease
     */
    private record Task(
            long id,
            long eventId,
            UUID subscriptionId,
            int attempts,
            UUID subject,
            String endpoint,
            String publicKey,
            String auth,
            Instant createdAt,
            String title,
            String message,
            String url,
            UUID lease) {}
}
