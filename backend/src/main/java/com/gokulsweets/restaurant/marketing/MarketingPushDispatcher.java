package com.gokulsweets.restaurant.marketing;

import com.gokulsweets.restaurant.customer.consent.*;
import com.gokulsweets.restaurant.customer.notification.*;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Independent bounded promotional worker; retries retain their ID and never touch order alerts. */
@Component
@RequiredArgsConstructor
public class MarketingPushDispatcher {
    private final MarketingPushService marketing;
    private final JdbcTemplate jdbc;
    private final CustomerAlertPreferences alerts;
    private final OptionalProcessingGate consent;
    private final WebPushTransport transport;
    private final TransactionTemplate transactions;
    private final WebPushProperties push;
    private final Clock inventoryClock;

    @Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    /** One leased network task, with no transaction held open across provider I/O. */
    record Task(
            long id,
            long branch,
            UUID subject,
            UUID subscription,
            String endpoint,
            String publicKey,
            String auth,
            String title,
            String body,
            String topic,
            int attempts,
            Instant expires,
            UUID lease) {}

    /** Evaluates enabled branches periodically, then dispatches a bounded batch. */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void scheduled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushDispatcher.class, "scheduled()");
        try {
            if (dedicatedImportWorker || !push.isSchedulerEnabled() || !marketing.enabled()) return;
            for (long branch : marketing.branches()) marketing.evaluate(branch);
            dispatchBatch();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushDispatcher.class, "scheduled()");
        }
    }

    /** Recovers expired leases, and makes at most five network attempts per tick. */
    public void dispatchBatch() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushDispatcher.class, "dispatchBatch()");
        try {
            if (!marketing.enabled()) return;
            jdbc.update(
                    "DELETE FROM marketing_push_deliveries WHERE id IN (SELECT id FROM"
                            + " marketing_push_deliveries WHERE environment=? AND created_at<? AND"
                            + " state NOT IN ('SENDING','QUEUED') ORDER BY id LIMIT 100)",
                    marketing.scope(),
                    Timestamp.from(inventoryClock.instant().minus(Duration.ofDays(90))));
            jdbc.update(
                    "UPDATE marketing_push_deliveries SET"
                        + " state='SKIPPED',lease_token=NULL,lease_until=NULL WHERE id IN (SELECT"
                        + " id FROM marketing_push_deliveries WHERE environment=? AND"
                        + " state='QUEUED' AND expires_at<=? ORDER BY id LIMIT 100)",
                    marketing.scope(),
                    Timestamp.from(inventoryClock.instant()));
            jdbc.update(
                    "UPDATE marketing_push_deliveries SET state=CASE WHEN attempts>=3 THEN 'FAILED'"
                        + " ELSE 'QUEUED' END,lease_token=NULL,lease_until=NULL WHERE environment=?"
                        + " AND state='SENDING' AND lease_until<?",
                    marketing.scope(),
                    Timestamp.from(inventoryClock.instant()));
            for (int i = 0; i < 5; i++) {
                var task = transactions.execute(status -> claim());
                if (task == null) return;
                if (!eligible(task)) {
                    finish(task, "SKIPPED", null);
                    continue;
                }
                try {
                    int code =
                            transport.sendMarketing(
                                    task.endpoint(),
                                    task.publicKey(),
                                    task.auth(),
                                    task.id(),
                                    task.title(),
                                    task.body(),
                                    "/branches/" + task.branch());
                    if (code >= 200 && code < 300) finish(task, "ACCEPTED", code);
                    else if (code == 404 || code == 410) {
                        jdbc.update(
                                "UPDATE customer_push_subscriptions SET"
                                        + " revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP) WHERE"
                                        + " id=? AND environment=?",
                                task.subscription(),
                                marketing.scope());
                        finish(task, "REVOKED", code);
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
                    __gokulMethodStartedNanos, MarketingPushDispatcher.class, "dispatchBatch()");
        }
    }

    /** Locks one due row; concurrent instances cannot claim the same delivery. */
    private Task claim() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushDispatcher.class, "claim()");
        try {
            var now = inventoryClock.instant();
            var rows =
                    jdbc.query(
                            """
SELECT d.*,s.endpoint,s.public_key,s.auth_secret FROM marketing_push_deliveries d
JOIN customer_push_subscriptions s ON s.id=d.subscription_id
WHERE d.environment=? AND d.state='QUEUED' AND d.next_attempt_at<=?
ORDER BY d.id LIMIT 1 FOR UPDATE OF d SKIP LOCKED
""",
                            (rs, row) ->
                                    new Task(
                                            rs.getLong("id"),
                                            rs.getLong("branch_id"),
                                            (UUID) rs.getObject("subject_id"),
                                            (UUID) rs.getObject("subscription_id"),
                                            rs.getString("endpoint"),
                                            rs.getString("public_key"),
                                            rs.getString("auth_secret"),
                                            rs.getString("title"),
                                            rs.getString("message"),
                                            rs.getString("topic"),
                                            rs.getInt("attempts") + 1,
                                            rs.getTimestamp("expires_at").toInstant(),
                                            UUID.randomUUID()),
                            marketing.scope(),
                            Timestamp.from(now));
            if (rows.isEmpty()) return null;
            var task = rows.getFirst();
            jdbc.update(
                    "UPDATE marketing_push_deliveries SET"
                            + " state='SENDING',attempts=?,lease_token=?,lease_until=? WHERE id=?",
                    task.attempts(),
                    task.lease(),
                    Timestamp.from(now.plusSeconds(30)),
                    task.id());
            return task;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushDispatcher.class, "claim()");
        }
    }

    /**
     * Rechecks branch pause, consent, session, quiet hours and recent purchases before every
     * attempt.
     */
    private boolean eligible(Task task) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushDispatcher.class, "eligible(Task)");
        try {
            var now = inventoryClock.instant();
            var config = marketing.settings(task.branch());
            var window = MarketingCopy.window(now);
            if (!marketing.enabled()
                    || !config.enabled()
                    || window == null
                    || !config.permits(window)
                    || !config.permits(task.topic())
                    || !task.expires().isAfter(now)
                    || !marketing.open(task.branch(), now)
                    || !consent.allows(
                            ConsentEnvironment.valueOf(marketing.scope()),
                            task.subject(),
                            ConsentPurpose.MARKETING)) return false;
            int reserved =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM marketing_push_deliveries WHERE environment=? AND"
                                    + " subject_id=? AND created_at>?",
                            Integer.class,
                            marketing.scope(),
                            task.subject(),
                            Timestamp.from(now.minus(Duration.ofDays(7))));
            if (reserved > config.weeklyLimit()) return false;
            var prefs = alerts.settings(marketing.scope(), task.subject());
            if (CustomerAlertPreferences.quiet(
                    prefs.quietHoursEnabled(),
                    prefs.quietStartMinute(),
                    prefs.quietEndMinute(),
                    now)) return false;
            return Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            """
SELECT EXISTS(SELECT 1 FROM customer_push_subscriptions s JOIN verified_customer_sessions v ON v.id=s.session_id
JOIN customer_notification_preferences p ON p.environment=s.environment AND p.subject_id=s.subject_id
WHERE s.id=? AND s.environment=? AND s.subject_id=? AND s.revoked_at IS NULL AND p.offer_inbox_enabled
 AND v.environment=s.environment AND v.verified_subject_id=s.subject_id AND v.revoked_at IS NULL AND v.expires_at>?
 AND NOT EXISTS(SELECT 1 FROM verified_order_ownership own JOIN orders o ON o.id=own.order_id
 WHERE own.environment=s.environment AND own.verified_subject_id=s.subject_id
 AND (o.created_at>=(CAST(? AS timestamptz) AT TIME ZONE 'Asia/Kolkata')-INTERVAL '24 hours' AND o.order_status NOT IN ('PAYMENT_FAILED','CANCELLED')
 OR o.order_status IN ('CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY','OUT_FOR_DELIVERY'))))
""",
                            Boolean.class,
                            task.subscription(),
                            marketing.scope(),
                            task.subject(),
                            Timestamp.from(now),
                            Timestamp.from(now)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushDispatcher.class, "eligible(Task)");
        }
    }

    /** Finishes only the claimed lease; provider acceptance is not proof of device delivery. */
    private void finish(Task task, String state, Integer code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushDispatcher.class, "finish(Task,String,Integer)");
        try {
            jdbc.update(
                    "UPDATE marketing_push_deliveries SET"
                        + " state=?,last_http_status=?,accepted_at=?,lease_token=NULL,lease_until=NULL"
                        + " WHERE id=? AND state='SENDING' AND lease_token=?",
                    state,
                    code,
                    "ACCEPTED".equals(state) ? Timestamp.from(inventoryClock.instant()) : null,
                    task.id(),
                    task.lease());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MarketingPushDispatcher.class,
                    "finish(Task,String,Integer)");
        }
    }

    /** Bounds transient retries; delayed or uncertain sends count against the customer's limits. */
    private void retry(Task task, Integer code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushDispatcher.class, "retry(Task,Integer)");
        try {
            if (task.attempts() >= 3
                    || !task.expires().isAfter(inventoryClock.instant().plusSeconds(60))) {
                finish(task, "FAILED", code);
                return;
            }
            jdbc.update(
                    "UPDATE marketing_push_deliveries SET"
                        + " state='QUEUED',last_http_status=?,next_attempt_at=?,lease_token=NULL,lease_until=NULL"
                        + " WHERE id=? AND state='SENDING' AND lease_token=?",
                    code,
                    Timestamp.from(inventoryClock.instant().plusSeconds(60)),
                    task.id(),
                    task.lease());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MarketingPushDispatcher.class,
                    "retry(Task,Integer)");
        }
    }
}
