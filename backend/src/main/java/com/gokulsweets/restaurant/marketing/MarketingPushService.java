package com.gokulsweets.restaurant.marketing;

import com.gokulsweets.restaurant.customer.consent.*;
import com.gokulsweets.restaurant.customer.notification.*;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Bounded behavioural recommendations and durable, consent-gated promotional reservations. */
@Service
@RequiredArgsConstructor
public class MarketingPushService {
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final CustomerAlertPreferences alerts;
    private final OptionalProcessingGate consent;
    private final WebPushTransport transport;
    private final TransactionTemplate transactions;
    private final Clock inventoryClock;

    /** Branch-owned settings; the version prevents stale admin tabs overwriting newer changes. */
    public record Settings(
            boolean enabled,
            String language,
            int weeklyLimit,
            boolean lunch,
            boolean snacks,
            boolean dinner,
            boolean sweets,
            long version) {
        /** Checks whether a message topic is enabled for this branch. */
        public boolean permits(String topic) {
            final long __gokulMethodStartedNanos =
                    MethodTiming.start(MarketingPushService.Settings.class, "permits(String)");
            try {
                return switch (topic) {
                    case "LUNCH" -> lunch;
                    case "SNACKS" -> snacks;
                    case "DINNER" -> dinner;
                    case "SWEETS" -> sweets;
                    default -> false;
                };
            } finally {
                MethodTiming.finish(
                        __gokulMethodStartedNanos,
                        MarketingPushService.Settings.class,
                        "permits(String)");
            }
        }
    }

    /** Anonymous aggregate visible to authorized branch managers. */
    public record View(
            Settings settings,
            boolean pushConfigured,
            boolean masterEnabled,
            String currentWindow,
            List<MarketingCopy.Message> samples,
            List<Map<String, Object>> recent) {}

    /** Internal verified identity and one most recently registered active device. */
    record Candidate(UUID subject, UUID subscription, String category, Long categoryId) {}

    /** Returns the configured environment, failing closed if isolation has no valid environment. */
    public String scope() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushService.class, "scope()");
        try {
            String value = environment.getProperty("gokul.environment-isolation.environment", "");
            return Set.of("DEV", "PROD").contains(value) ? value : "";
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MarketingPushService.class, "scope()");
        }
    }

    /** Returns the emergency master switch; delivery also requires branch opt-in. */
    public boolean enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushService.class, "enabled()");
        try {
            return !scope().isEmpty()
                    && alerts.enabled()
                    && transport.configured()
                    && environment.getProperty(
                            "gokul.notifications.marketing.enabled", Boolean.class, false);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, MarketingPushService.class, "enabled()");
        }
    }

    /** Reads branch settings, with disabled defaults for every branch on migration. */
    public Settings settings(long branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushService.class, "settings(long)");
        try {
            return jdbc
                    .query(
                            "SELECT * FROM marketing_push_settings WHERE environment=? AND"
                                    + " branch_id=?",
                            (rs, row) ->
                                    new Settings(
                                            rs.getBoolean("enabled"),
                                            rs.getString("language"),
                                            rs.getInt("weekly_limit"),
                                            rs.getBoolean("lunch"),
                                            rs.getBoolean("snacks"),
                                            rs.getBoolean("dinner"),
                                            rs.getBoolean("sweets"),
                                            rs.getLong("version")),
                            scope(),
                            branch)
                    .stream()
                    .findFirst()
                    .orElse(new Settings(false, "HINGLISH", 3, true, true, true, true, 0));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushService.class, "settings(long)");
        }
    }

    /**
     * Returns previews and provider-acceptance history, never customer identities or device
     * secrets.
     */
    public View view(long branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushService.class, "view(long)");
        try {
            var config = settings(branch);
            return new View(
                    config,
                    transport.configured(),
                    enabled(),
                    MarketingCopy.window(inventoryClock.instant()),
                    MarketingCopy.samples(config.language()),
                    jdbc.queryForList(
                            "SELECT topic,title,message,reason,state,created_at,accepted_at FROM"
                                + " marketing_push_deliveries WHERE environment=? AND branch_id=?"
                                + " ORDER BY id DESC LIMIT 30",
                            scope(),
                            branch));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushService.class, "view(long)");
        }
    }

    /** Updates branch settings atomically after a compare-and-set version check. */
    public Settings save(long branch, Settings input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushService.class, "save(long,Settings)");
        try {
            if (input == null
                    || !Set.of("EN", "HINGLISH").contains(Objects.toString(input.language(), ""))
                    || input.weeklyLimit() < 1
                    || input.weeklyLimit() > 3
                    || input.version() < 0)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Choose a language and 1–3 messages per week.");
            transactions.executeWithoutResult(
                    status -> {
                        jdbc.update(
                                "INSERT INTO marketing_push_settings(environment,branch_id) VALUES"
                                        + " (?,?) ON CONFLICT DO NOTHING",
                                scope(),
                                branch);
                        int changed =
                                jdbc.update(
                                        "UPDATE marketing_push_settings SET"
                                            + " enabled=?,language=?,weekly_limit=?,lunch=?,snacks=?,dinner=?,sweets=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
                                            + " WHERE environment=? AND branch_id=? AND version=?",
                                        input.enabled(),
                                        input.language(),
                                        input.weeklyLimit(),
                                        input.lunch(),
                                        input.snacks(),
                                        input.dinner(),
                                        input.sweets(),
                                        scope(),
                                        branch,
                                        input.version());
                        if (changed != 1)
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT, "Settings changed. Reload before saving.");
                    });
            return settings(branch);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushService.class, "save(long,Settings)");
        }
    }

    /**
     * Selects no more than fifty eligible recent customers, using indexed histories and current
     * consent.
     */
    List<Candidate> candidates(long branch, Settings config, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushService.class, "candidates(long,Settings,Instant)");
        try {
            var params =
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("env", scope())
                            .addValue("branch", branch)
                            .addValue("now", Timestamp.from(now))
                            .addValue(
                                    "day",
                                    java.sql.Date.valueOf(
                                            now.atZone(MarketingCopy.INDIA).toLocalDate()))
                            .addValue("window", MarketingCopy.window(now))
                            .addValue("language", config.language())
                            .addValue("sweets", config.sweets())
                            .addValue("weekLimit", config.weeklyLimit())
                            .addValue(
                                    "policy",
                                    environment.getProperty("gokul.consent.policy-version", ""));
            return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc)
                    .query(
                            """
       SELECT s.subject_id,s.id, fav.name, fav.id FROM customer_push_subscriptions s
       JOIN verified_customer_sessions v ON v.id=s.session_id
       JOIN customer_notification_preferences pref ON pref.environment=s.environment AND pref.subject_id=s.subject_id
       LEFT JOIN LATERAL (
        SELECT c.id,c.name FROM verified_order_ownership own JOIN orders o ON o.id=own.order_id
        JOIN order_items oi ON oi.order_id=o.id JOIN products p ON p.id=oi.product_id JOIN categories c ON c.id=p.category_id
        WHERE own.environment=s.environment AND own.verified_subject_id=s.subject_id AND o.branch_id=:branch
        AND o.order_status IN ('PICKED_UP','DELIVERED') AND o.created_at>=(CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata')-INTERVAL '90 days'
        AND p.active AND c.active AND EXISTS (SELECT 1 FROM branch_products bp WHERE bp.branch_id=:branch AND bp.product_id=p.id AND bp.available AND NOT bp.occasion_only)
        GROUP BY c.id,c.name ORDER BY count(DISTINCT o.id) DESC,max(o.created_at) DESC,c.id LIMIT 1
       ) fav ON TRUE
       WHERE s.environment=:env AND s.revoked_at IS NULL AND pref.offer_inbox_enabled
        AND v.environment=s.environment AND v.verified_subject_id=s.subject_id
        AND v.revoked_at IS NULL AND v.expires_at>:now
        AND s.id=(SELECT newer.id FROM customer_push_subscriptions newer JOIN verified_customer_sessions nv ON nv.id=newer.session_id
          WHERE newer.environment=s.environment AND newer.subject_id=s.subject_id AND newer.revoked_at IS NULL
          AND nv.environment=s.environment AND nv.verified_subject_id=s.subject_id AND nv.revoked_at IS NULL AND nv.expires_at>:now
          ORDER BY newer.subscribed_at DESC,newer.id DESC LIMIT 1)
        AND (SELECT latest.branch_id FROM verified_order_ownership own JOIN orders latest ON latest.id=own.order_id
          WHERE own.environment=s.environment AND own.verified_subject_id=s.subject_id
          AND latest.order_status IN ('PICKED_UP','DELIVERED') AND latest.created_at>=(CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata')-INTERVAL '365 days'
          ORDER BY latest.created_at DESC,latest.id DESC LIMIT 1)=:branch
        AND EXISTS (SELECT 1 FROM customer_consent_events ce WHERE ce.id=(SELECT max(ce2.id) FROM customer_consent_events ce2
          WHERE ce2.environment=s.environment AND ce2.verified_subject_id=s.subject_id AND ce2.purpose='MARKETING')
          AND ce.granted AND ce.policy_version=:policy)
        AND NOT EXISTS (SELECT 1 FROM marketing_push_deliveries d WHERE d.environment=s.environment AND d.subject_id=s.subject_id AND d.send_date=:day)
        AND (SELECT count(*) FROM marketing_push_deliveries d WHERE d.environment=s.environment AND d.subject_id=s.subject_id AND d.created_at>CAST(:now AS timestamptz)-INTERVAL '7 days')<:weekLimit
        AND NOT EXISTS (SELECT 1 FROM verified_order_ownership own JOIN orders o ON o.id=own.order_id
          WHERE own.environment=s.environment AND own.verified_subject_id=s.subject_id
          AND (o.created_at>=(CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata')-INTERVAL '24 hours' AND o.order_status NOT IN ('PAYMENT_FAILED','CANCELLED')
            OR o.order_status IN ('CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY','OUT_FOR_DELIVERY')))
        AND (NOT pref.quiet_hours_enabled OR CASE WHEN pref.quiet_start_minute<pref.quiet_end_minute
          THEN NOT ((extract(hour FROM CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata')*60+extract(minute FROM CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata'))>=pref.quiet_start_minute
            AND (extract(hour FROM CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata')*60+extract(minute FROM CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata'))<pref.quiet_end_minute)
          ELSE (extract(hour FROM CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata')*60+extract(minute FROM CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata'))>=pref.quiet_end_minute
            AND (extract(hour FROM CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata')*60+extract(minute FROM CAST(:now AS timestamptz) AT TIME ZONE 'Asia/Kolkata'))<pref.quiet_start_minute END)
        AND (:window='SNACKS' OR (lower(COALESCE(fav.name,'')) NOT LIKE '%snack%' AND lower(COALESCE(fav.name,'')) NOT LIKE '%breakfast%'))
AND (SELECT count(DISTINCT used.template_key) FROM marketing_push_deliveries used
  WHERE used.environment=s.environment AND used.subject_id=s.subject_id AND used.created_at>CAST(:now AS timestamptz)-INTERVAL '14 days'
  AND used.template_key LIKE ((CASE WHEN :sweets AND lower(COALESCE(fav.name,'')) LIKE '%sweet%' THEN 'SWEETS' ELSE :window END)||':'||:language||':%'))<4
ORDER BY s.subscribed_at DESC,s.id LIMIT 50
""",
                            params,
                            (rs, row) ->
                                    new Candidate(
                                            (UUID) rs.getObject(1),
                                            (UUID) rs.getObject(2),
                                            rs.getString(3),
                                            (Long) rs.getObject(4)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MarketingPushService.class,
                    "candidates(long,Settings,Instant)");
        }
    }

    /** Resolves current opening hours; absent branch hours do not imply permission to send. */
    boolean open(long branch, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushService.class, "open(long,Instant)");
        try {
            var time = now.atZone(MarketingCopy.INDIA).toLocalTime();
            return Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            """
SELECT EXISTS(SELECT 1 FROM branches WHERE id=? AND active AND opening_time IS NOT NULL AND closing_time IS NOT NULL AND opening_time<>closing_time
AND CASE WHEN opening_time<closing_time THEN CAST(? AS time)>=opening_time AND CAST(? AS time)<closing_time
 ELSE CAST(? AS time)>=opening_time OR CAST(? AS time)<closing_time END
AND EXISTS(SELECT 1 FROM branch_products bp JOIN products p ON p.id=bp.product_id JOIN categories c ON c.id=p.category_id WHERE bp.branch_id=branches.id AND bp.available AND p.active AND c.active AND NOT bp.occasion_only))
""",
                            Boolean.class,
                            branch,
                            time,
                            time,
                            time,
                            time));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushService.class, "open(long,Instant)");
        }
    }

    /**
     * Uses an environment queue lock and global daily uniqueness to reserve messages without
     * sending in a transaction.
     */
    public int evaluate(long branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushService.class, "evaluate(long)");
        try {
            if (!enabled()) return 0;
            return Objects.requireNonNullElse(
                    transactions.execute(
                            status -> {
                                jdbc.query(
                                        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                                        rs -> {},
                                        scope() + ":marketing-queue");
                                var config = settings(branch);
                                var now = inventoryClock.instant();
                                var window = MarketingCopy.window(now);
                                if (!config.enabled()
                                        || window == null
                                        || !config.permits(window)
                                        || !open(branch, now)) return 0;
                                int pending =
                                        jdbc.queryForObject(
                                                "SELECT count(*) FROM marketing_push_deliveries"
                                                        + " WHERE environment=? AND state IN"
                                                        + " ('QUEUED','SENDING') AND expires_at>?",
                                                Integer.class,
                                                scope(),
                                                Timestamp.from(now));
                                int capacity = Math.min(5, 25 - pending);
                                if (capacity <= 0) return 0;
                                int queued = 0;
                                for (var candidate : candidates(branch, config, now)) {
                                    if (queued >= capacity) break;
                                    if (!consent.allows(
                                            ConsentEnvironment.valueOf(scope()),
                                            candidate.subject(),
                                            ConsentPurpose.MARKETING)) continue;
                                    String preference =
                                            MarketingCopy.preference(candidate.category());
                                    String topic =
                                            "SWEETS".equals(preference) && config.sweets()
                                                    ? "SWEETS"
                                                    : window;
                                    if ("SNACKS".equals(preference) && !"SNACKS".equals(window))
                                        continue;
                                    var recent =
                                            new HashSet<>(
                                                    jdbc.queryForList(
                                                            "SELECT template_key FROM"
                                                                + " marketing_push_deliveries WHERE"
                                                                + " environment=? AND subject_id=?"
                                                                + " AND created_at>?",
                                                            String.class,
                                                            scope(),
                                                            candidate.subject(),
                                                            Timestamp.from(
                                                                    now.minus(
                                                                            Duration.ofDays(14)))));
                                    var copy =
                                            MarketingCopy.choose(
                                                    candidate.subject(),
                                                    now.atZone(MarketingCopy.INDIA).toLocalDate(),
                                                    topic,
                                                    config.language(),
                                                    recent);
                                    if (copy == null) continue;
                                    queued +=
                                            jdbc.update(
                                                    """
INSERT INTO marketing_push_deliveries(environment,branch_id,subject_id,subscription_id,send_date,template_key,topic,title,message,reason,expires_at,created_at,next_attempt_at,promoted_category_id)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(environment,subject_id,send_date) DO NOTHING
""",
                                                    scope(),
                                                    branch,
                                                    candidate.subject(),
                                                    candidate.subscription(),
                                                    java.sql.Date.valueOf(
                                                            now.atZone(MarketingCopy.INDIA)
                                                                    .toLocalDate()),
                                                    copy.key(),
                                                    copy.topic(),
                                                    copy.title(),
                                                    copy.body(),
                                                    preference == null
                                                            ? "Meal time at your most recent branch"
                                                            : "Meal time + past category"
                                                                    + " preference",
                                                    Timestamp.from(now.plusSeconds(900)),
                                                    Timestamp.from(now),
                                                    Timestamp.from(now),
                                                    topic.equals(preference)
                                                            ? candidate.categoryId()
                                                            : null);
                                }
                                return queued;
                            }),
                    0);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushService.class, "evaluate(long)");
        }
    }

    /** Returns enabled branches in a bounded page for automatic scheduling. */
    public List<Long> branches() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MarketingPushService.class, "branches()");
        try {
            return jdbc.queryForList(
                    "SELECT branch_id FROM marketing_push_settings WHERE environment=? AND enabled"
                            + " ORDER BY branch_id LIMIT 100",
                    Long.class,
                    scope());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MarketingPushService.class, "branches()");
        }
    }
}
