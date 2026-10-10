package com.gokulsweets.restaurant.badges;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/** Badge progress is scoped to verified ownership. No badge read delays public menu loading. */
@Service
@RequiredArgsConstructor
public class CustomerBadgeService {
    private final JdbcTemplate jdbc;

    /** An active definition and its verified customer qualifying-order progress. */
    public record Badge(
            long id,
            String code,
            String name,
            String description,
            int requiredOrders,
            BigDecimal minimumSubtotal,
            BigDecimal bonusPercent,
            String appearance,
            boolean active,
            int version,
            long qualifyingOrders,
            boolean earned) {}

    /** Active badge progress and the single highest currently qualifying tier. */
    public record Snapshot(List<Badge> badges, Badge current) {}

    /** A subject-bound recognition lease with the customer's current highest-tier benefit. */
    public record Celebration(
            long awardId,
            UUID claimId,
            String name,
            String description,
            BigDecimal bonusPercent,
            String appearance) {}

    /** The one applicable bonus percentage and badge name saved on a new order. */
    public record Benefit(BigDecimal percent, String name) {}

    private static final String COMPLETED =
            """
SELECT o.id,GREATEST(o.subtotal-COALESCE(o.rebate_discount_amount,0)-COALESCE(o.loyalty_discount,0),0) AS spend
FROM orders o JOIN verified_order_ownership own ON own.order_id=o.id
WHERE own.environment=? AND own.verified_subject_id=?
  AND o.order_status IN ('PICKED_UP','DELIVERED') AND NOT o.loyalty_test_order
  AND EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID')
  AND NOT EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status IN ('REFUND_PENDING','REFUNDED','REFUND_FAILED'))
""";

    /** Counts completed paid owner-bound orders meeting each active badge threshold. */
    @Transactional(readOnly = true)
    public List<Badge> progress(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerBadgeService.class, "progress(String,UUID)");
        try {
            return jdbc.query(
                    "WITH completed AS ("
                            + COMPLETED
                            + ") "
                            + """
SELECT b.*,COUNT(c.id) AS qualifying FROM customer_badges b
LEFT JOIN completed c ON c.spend>=b.minimum_subtotal
WHERE b.active GROUP BY b.id ORDER BY b.required_orders,b.minimum_subtotal,b.id
""",
                    (rs, row) ->
                            new Badge(
                                    rs.getLong("id"),
                                    rs.getString("code"),
                                    rs.getString("name"),
                                    rs.getString("description"),
                                    rs.getInt("required_orders"),
                                    rs.getBigDecimal("minimum_subtotal"),
                                    rs.getBigDecimal("bonus_percent"),
                                    rs.getString("appearance"),
                                    rs.getBoolean("active"),
                                    rs.getInt("version"),
                                    rs.getLong("qualifying"),
                                    rs.getLong("qualifying") >= rs.getInt("required_orders")),
                    environment,
                    subject);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerBadgeService.class, "progress(String,UUID)");
        }
    }

    /** Records first-time recognition and returns current qualifying badge progress. */
    @Transactional
    public Snapshot snapshot(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerBadgeService.class, "snapshot(String,UUID)");
        try {
            var badges = progress(environment, subject);
            for (var badge : badges)
                if (badge.earned())
                    jdbc.update(
                            """
INSERT INTO customer_badge_awards(environment,subject_id,badge_id,name,description,bonus_percent,appearance)
VALUES(?,?,?,?,?,?,?) ON CONFLICT(environment,subject_id,badge_id) DO NOTHING
""",
                            environment,
                            subject,
                            badge.id(),
                            badge.name(),
                            badge.description(),
                            badge.bonusPercent(),
                            badge.appearance());
            var current =
                    badges.stream()
                            .filter(Badge::earned)
                            .max(
                                    java.util.Comparator.comparing(Badge::requiredOrders)
                                            .thenComparing(Badge::minimumSubtotal)
                                            .thenComparing(Badge::id))
                            .orElse(null);
            return new Snapshot(badges, current);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerBadgeService.class, "snapshot(String,UUID)");
        }
    }

    /** A new order uses one highest tier; policy edits cannot change an already-enrolled order. */
    @Transactional(readOnly = true)
    public Benefit benefit(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerBadgeService.class, "benefit(String,UUID)");
        try {
            var current =
                    progress(environment, subject).stream()
                            .filter(Badge::earned)
                            .max(
                                    java.util.Comparator.comparing(Badge::requiredOrders)
                                            .thenComparing(Badge::minimumSubtotal)
                                            .thenComparing(Badge::id))
                            .orElse(null);
            return current == null
                    ? new Benefit(BigDecimal.ZERO, null)
                    : new Benefit(current.bonusPercent(), current.name());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerBadgeService.class, "benefit(String,UUID)");
        }
    }

    /** Rounds the configured percentage of normal earned coins down to whole coins. */
    public static int bonus(int base, BigDecimal percent) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerBadgeService.class, "bonus(int,BigDecimal)");
        try {
            if (base <= 0 || percent == null || percent.signum() <= 0) return 0;
            return BigDecimal.valueOf(base)
                    .multiply(percent)
                    .divide(new BigDecimal("100"), 0, RoundingMode.DOWN)
                    .intValueExact();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerBadgeService.class, "bonus(int,BigDecimal)");
        }
    }

    /** One short durable claim prevents two tabs/devices celebrating the same award together. */
    @Transactional
    public Celebration claim(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerBadgeService.class, "claim(String,UUID)");
        try {
            var snapshot = snapshot(environment, subject);
            var earned =
                    snapshot.badges().stream()
                            .filter(Badge::earned)
                            .map(b -> Long.toString(b.id()))
                            .collect(java.util.stream.Collectors.joining(","));
            if (earned.isEmpty()) return null;
            UUID claim = UUID.randomUUID();
            var values =
                    jdbc.query(
                            """
WITH chosen AS (
  SELECT a.id FROM customer_badge_awards a JOIN customer_badges b ON b.id=a.badge_id
  WHERE a.environment=? AND a.subject_id=? AND a.celebrated_at IS NULL AND b.active
    AND a.badge_id = ANY(string_to_array(?,',')::bigint[])
    AND (a.claim_until IS NULL OR a.claim_until<CURRENT_TIMESTAMP)
  ORDER BY a.earned_at,a.id LIMIT 1 FOR UPDATE OF a SKIP LOCKED
)
UPDATE customer_badge_awards a SET claim_id=?,claim_until=CURRENT_TIMESTAMP+INTERVAL '2 minutes'
FROM chosen WHERE a.id=chosen.id
RETURNING a.id,a.name,a.description,a.appearance
""",
                            (rs, row) ->
                                    new Celebration(
                                            rs.getLong(1),
                                            claim,
                                            rs.getString(2),
                                            rs.getString(3),
                                            snapshot.current().bonusPercent(),
                                            rs.getString(4)),
                            environment,
                            subject,
                            earned,
                            claim);
            return values.isEmpty() ? null : values.getFirst();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerBadgeService.class, "claim(String,UUID)");
        }
    }

    /** Durably marks a presented badge using its subject-bound claim token. */
    @Transactional
    public void acknowledge(String environment, UUID subject, long awardId, UUID claim) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerBadgeService.class, "acknowledge(String,UUID,long,UUID)");
        try {
            if (claim == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Badge claim required.");
            int changed =
                    jdbc.update(
                            """
UPDATE customer_badge_awards SET celebrated_at=COALESCE(celebrated_at,CURRENT_TIMESTAMP),claim_until=NULL
WHERE id=? AND environment=? AND subject_id=? AND claim_id=?
""",
                            awardId,
                            environment,
                            subject,
                            claim);
            if (changed == 0)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Badge presentation changed. Refresh your badges.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerBadgeService.class,
                    "acknowledge(String,UUID,long,UUID)");
        }
    }
}
