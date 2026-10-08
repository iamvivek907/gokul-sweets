package com.gokulsweets.restaurant.loyalty;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.service.PaymentFeePricing;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Every mutation locks one account. Ledger events and projections commit with the order. */
@Service
@RequiredArgsConstructor
public class LoyaltyService {

    private final JdbcTemplate jdbc;

    private final EnhancementProperties features;

    private final LoyaltyProperties rules;

    private final Clock inventoryClock;

    /** Immutable reward data contract. */
    public record Reward(
            String code,
            String name,
            int coins,
            BigDecimal discount,
            BigDecimal minimumSubtotal,
            boolean eligible,
            String unavailableReason) {}

    /** Immutable entry data contract. */
    public record Entry(
            long id,
            String kind,
            int coins,
            String reason,
            String orderNumber,
            Long customerOrderNumber,
            Instant createdAt,
            Instant expiresAt) {}

    /** Immutable wallet data contract. */
    public record Wallet(
            int balance,
            int debt,
            int pendingCoins,
            int completedOrders,
            List<Reward> rewards,
            List<Entry> history,
            Instant nextExpiry,
            BigDecimal maximumRedemptionPercent,
            String terms,
            String policyVersion) {}

    /** Immutable selection data contract. */
    public record Selection(String rewardCode, String policyVersion) {}

    /** Immutable lot data contract. */
    private record Lot(long id, int remaining, Instant expiry, Long origin) {

        Lot(long id, int remaining, Instant expiry) {
            this(id, remaining, expiry, null);
        }
    }

    /** Immutable owner data contract. */
    private record Owner(String environment, UUID subject) {}

    /**
     * Enableds the operation.
     *
     * @return the enabled result
     */
    public boolean enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "enabled()");
        try {
            return features.rewardsReady();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, LoyaltyService.class, "enabled()");
        }
    }

    // One transaction-scoped lock covers catalogue rows and both exclusion sets, including
    // insertions.
    private static final int POLICY_LOCK_NAMESPACE =
            AppConstant.LOYALTY_SERVICE_POLICY_LOCK_NAMESPACE;

    /** Locks policy for read. */
    public void lockPolicyForRead() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "lockPolicyForRead()");
        try {
            jdbc.execute("SELECT pg_advisory_xact_lock_shared(" + POLICY_LOCK_NAMESPACE + ",38)");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "lockPolicyForRead()");
        }
    }

    /** Locks policy for update. */
    public void lockPolicyForUpdate() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "lockPolicyForUpdate()");
        try {
            jdbc.execute("SELECT pg_advisory_xact_lock(" + POLICY_LOCK_NAMESPACE + ",38)");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "lockPolicyForUpdate()");
        }
    }

    /**
     * Policy version.
     *
     * @return the policy version result
     */
    public String policyVersion() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "policyVersion()");
        try {
            return policyVersion(false);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, LoyaltyService.class, "policyVersion()");
        }
    }

    /**
     * Locks policy version.
     *
     * @return the lock policy version result
     */
    public String lockPolicyVersion() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "lockPolicyVersion()");
        try {
            return policyVersion(true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "lockPolicyVersion()");
        }
    }

    /**
     * Policy version.
     *
     * @param lock the lock
     * @return the policy version result
     */
    private String policyVersion(boolean lock) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "policyVersion(boolean)");
        try {
            if (!enabled()) return "OFF";
            if (lock) lockPolicyForRead();
            String policy =
                    jdbc.queryForList(
                                            "SELECT"
                                                + " code,version,coins,discount,minimum_subtotal,active"
                                                + " FROM loyalty_rewards ORDER BY code")
                                    .toString()
                            + jdbc.queryForList(
                                            "SELECT product_id FROM loyalty_excluded_products ORDER"
                                                    + " BY product_id")
                                    .toString()
                            + jdbc.queryForList(
                                            "SELECT code FROM loyalty_excluded_rebates ORDER BY"
                                                    + " code")
                                    .toString()
                            + "|"
                            + rules.getRupeesPerCoin()
                            + "|"
                            + rules.getRedemptionPercent()
                            + "|"
                            + rules.getExpiryDays()
                            + "|"
                            + rules.getQualifyingSubtotal()
                            + "|"
                            + rules.getWelcomeCoins();
            try {
                return java.util.HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                policy.getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "policyVersion(boolean)");
        }
    }

    /**
     * Verify policy.
     *
     * @param version the version
     */
    public void verifyPolicy(String version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "verifyPolicy(String)");
        try {
            if (version == null || !lockPolicyVersion().equals(version))
                throw invalid(
                        "Reward rules changed. Refresh your earned coins before selecting a"
                                + " reward.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "verifyPolicy(String)");
        }
    }

    /**
     * Nows the operation.
     *
     * @return the now result
     */
    private Instant now() {
        final long __gokulMethodStartedNanos = MethodTiming.start(LoyaltyService.class, "now()");
        try {
            return inventoryClock.instant();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, LoyaltyService.class, "now()");
        }
    }

    /**
     * Timestamps the operation.
     *
     * @param value the value
     * @return the timestamp result
     */
    private Timestamp timestamp(Instant value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "timestamp(Instant)");
        try {
            return value == null ? null : Timestamp.from(value);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "timestamp(Instant)");
        }
    }

    /**
     * Invalids the operation.
     *
     * @param message the message
     * @return the invalid result
     */
    private ResponseStatusException invalid(String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "invalid(String)");
        try {
            return new ResponseStatusException(HttpStatus.CONFLICT, message);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, LoyaltyService.class, "invalid(String)");
        }
    }

    /**
     * Locks the operation.
     *
     * @param environment the environment
     * @param subject the subject
     */
    private void lock(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "lock(String,UUID)");
        try {
            jdbc.update(
                    "INSERT INTO loyalty_accounts(environment,subject_id) VALUES (?,?) ON CONFLICT"
                            + " DO NOTHING",
                    environment,
                    subject);
            jdbc.queryForObject(
                    "SELECT subject_id FROM loyalty_accounts WHERE environment=? AND subject_id=?"
                            + " FOR UPDATE",
                    UUID.class,
                    environment,
                    subject);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "lock(String,UUID)");
        }
    }

    /**
     * Lotses the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @return the lots result
     */
    private List<Lot> lots(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "lots(String,UUID)");
        try {
            return jdbc.query(
                    """
SELECT l.id,p.remaining,GREATEST(l.expires_at,a.last_qualifying_activity + (l.expiry_days * INTERVAL '1 day')) AS expires_at,l.source_credit_id FROM loyalty_lots p JOIN loyalty_ledger l ON l.id=p.ledger_id JOIN loyalty_accounts a ON a.environment=l.environment AND a.subject_id=l.subject_id
WHERE l.environment=? AND l.subject_id=? AND p.remaining>0 ORDER BY expires_at NULLS LAST,l.id
""",
                    (rs, row) ->
                            new Lot(
                                    rs.getLong(1),
                                    rs.getInt(2),
                                    rs.getTimestamp(3) == null
                                            ? null
                                            : rs.getTimestamp(3).toInstant(),
                                    (Long) rs.getObject(4)),
                    environment,
                    subject);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "lots(String,UUID)");
        }
    }

    /**
     * Signeds balance.
     *
     * @param environment the environment
     * @param subject the subject
     * @return the signed balance result
     */
    private int signedBalance(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "signedBalance(String,UUID)");
        try {
            return jdbc.queryForObject(
                    "SELECT COALESCE(SUM(coins),0)::integer FROM loyalty_ledger WHERE environment=?"
                            + " AND subject_id=?",
                    Integer.class,
                    environment,
                    subject);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "signedBalance(String,UUID)");
        }
    }

    /**
     * Appends the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @param key the key
     * @param kind the kind
     * @param coins the coins
     * @param orderId the order id
     * @param reason the reason
     * @param expiry the expiry
     * @return the append result
     */
    private long append(
            String environment,
            UUID subject,
            String key,
            String kind,
            int coins,
            Long orderId,
            String reason,
            Instant expiry) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        LoyaltyService.class,
                        "append(String,UUID,String,String,int,Long,String,Instant)");
        try {
            return append(environment, subject, key, kind, coins, orderId, reason, expiry, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyService.class,
                    "append(String,UUID,String,String,int,Long,String,Instant)");
        }
    }

    /**
     * Appends the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @param key the key
     * @param kind the kind
     * @param coins the coins
     * @param orderId the order id
     * @param reason the reason
     * @param expiry the expiry
     * @param origin the origin
     * @return the append result
     */
    private long append(
            String environment,
            UUID subject,
            String key,
            String kind,
            int coins,
            Long orderId,
            String reason,
            Instant expiry,
            Long origin) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        LoyaltyService.class,
                        "append(String,UUID,String,String,int,Long,String,Instant,Long)");
        try {
            return append(
                    environment,
                    subject,
                    key,
                    kind,
                    coins,
                    orderId,
                    reason,
                    expiry,
                    origin,
                    rules.getExpiryDays());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyService.class,
                    "append(String,UUID,String,String,int,Long,String,Instant,Long)");
        }
    }

    /**
     * Appends the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @param key the key
     * @param kind the kind
     * @param coins the coins
     * @param orderId the order id
     * @param reason the reason
     * @param expiry the expiry
     * @param origin the origin
     * @param expiryDays the expiry days
     * @return the append result
     */
    private long append(
            String environment,
            UUID subject,
            String key,
            String kind,
            int coins,
            Long orderId,
            String reason,
            Instant expiry,
            Long origin,
            int expiryDays) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        LoyaltyService.class,
                        "append(String,UUID,String,String,int,Long,String,Instant,Long,int)");
        try {
            int days =
                    origin == null
                            ? expiryDays
                            : jdbc.queryForObject(
                                    "SELECT expiry_days FROM loyalty_ledger WHERE id=?",
                                    Integer.class,
                                    origin);
            var ids =
                    jdbc.query(
                            """
INSERT INTO loyalty_ledger(environment,subject_id,event_key,kind,coins,order_id,reason,expires_at,created_at,source_credit_id,expiry_days)
VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(environment,subject_id,event_key) DO NOTHING RETURNING id
""",
                            (rs, row) -> rs.getLong(1),
                            environment,
                            subject,
                            key,
                            kind,
                            coins,
                            orderId,
                            reason,
                            timestamp(expiry),
                            timestamp(now()),
                            origin,
                            days);
            if (ids.isEmpty()) return 0;
            long id = ids.getFirst();
            if (coins > 0)
                jdbc.update(
                        "INSERT INTO loyalty_lots(ledger_id,remaining) VALUES (?,?)",
                        id,
                        Math.min(coins, Math.max(0, signedBalance(environment, subject))));
            return id;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyService.class,
                    "append(String,UUID,String,String,int,Long,String,Instant,Long,int)");
        }
    }

    /**
     * Expires the operation.
     *
     * @param environment the environment
     * @param subject the subject
     */
    private void expire(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "expire(String,UUID)");
        try {
            for (var lot : lots(environment, subject))
                if (lot.expiry() != null && !lot.expiry().isAfter(now())) {
                    append(
                            environment,
                            subject,
                            "expiry:" + lot.id(),
                            "EXPIRED",
                            -lot.remaining(),
                            null,
                            "Coins reached their displayed expiry date",
                            null,
                            lot.origin() == null ? lot.id() : lot.origin());
                    jdbc.update("UPDATE loyalty_lots SET remaining=0 WHERE ledger_id=?", lot.id());
                }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "expire(String,UUID)");
        }
    }

    /**
     * Existses the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @param key the key
     * @return the exists result
     */
    private boolean exists(String environment, UUID subject, String key) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "exists(String,UUID,String)");
        try {
            return Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM loyalty_ledger WHERE environment=? AND"
                                    + " subject_id=? AND event_key=?)",
                            Boolean.class,
                            environment,
                            subject,
                            key));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "exists(String,UUID,String)");
        }
    }

    /**
     * Excludeds products.
     *
     * @return the excluded products result
     */
    public Set<Long> excludedProducts() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "excludedProducts()");
        try {
            return new HashSet<>(
                    jdbc.queryForList(
                            "SELECT product_id FROM loyalty_excluded_products", Long.class));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "excludedProducts()");
        }
    }

    /**
     * Eligibles subtotal.
     *
     * @param order the order
     * @return the eligible subtotal result
     */
    public BigDecimal eligibleSubtotal(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "eligibleSubtotal(Order)");
        try {
            var excluded = excludedProducts();
            return order.getItems().stream()
                    .filter(item -> !excluded.contains(item.getProduct().getId()))
                    .map(item -> item.getLineTotal().subtract(item.getTaxAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .max(BigDecimal.ZERO);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "eligibleSubtotal(Order)");
        }
    }

    /**
     * Expires account.
     *
     * @param environment the environment
     * @param subject the subject
     */
    @Transactional
    public void expireAccount(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "expireAccount(String,UUID)");
        try {
            lock(environment, subject);
            expire(environment, subject);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "expireAccount(String,UUID)");
        }
    }

    /**
     * Caps the operation.
     *
     * @param subtotal the subtotal
     * @return the cap result
     */
    public BigDecimal cap(BigDecimal subtotal) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "cap(BigDecimal)");
        try {
            return subtotal.max(BigDecimal.ZERO)
                    .multiply(rules.getRedemptionPercent())
                    .divide(new BigDecimal("100"), 2, RoundingMode.DOWN);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, LoyaltyService.class, "cap(BigDecimal)");
        }
    }

    /**
     * Catalogues the operation.
     *
     * @param balance the balance
     * @param subtotal the subtotal
     * @return the catalogue result
     */
    private List<Reward> catalogue(int balance, BigDecimal subtotal) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "catalogue(int,BigDecimal)");
        try {
            return jdbc.query(
                    "SELECT code,name,coins,discount,minimum_subtotal FROM loyalty_rewards WHERE"
                            + " active ORDER BY coins,code",
                    (rs, row) -> {
                        int coins = rs.getInt(3);
                        var discount = rs.getBigDecimal(4);
                        var minimum = rs.getBigDecimal(5);
                        String reason =
                                balance < coins
                                        ? "Not enough coins"
                                        : subtotal == null
                                                ? "Choose an order to check eligibility"
                                                : subtotal.compareTo(minimum) < 0
                                                        ? "Minimum product subtotal ₹" + minimum
                                                        : discount.compareTo(cap(subtotal)) > 0
                                                                ? "Exceeds this order’s "
                                                                        + rules
                                                                                .getRedemptionPercent()
                                                                        + "% reward limit"
                                                                : null;
                        return new Reward(
                                rs.getString(1),
                                rs.getString(2),
                                coins,
                                discount,
                                minimum,
                                reason == null,
                                reason);
                    });
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "catalogue(int,BigDecimal)");
        }
    }

    /**
     * Wallets the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @param subtotal the subtotal
     * @return the wallet result
     */
    @Transactional
    public Wallet wallet(String environment, UUID subject, BigDecimal subtotal) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "wallet(String,UUID,BigDecimal)");
        try {
            if (!enabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            lock(environment, subject);
            expire(environment, subject);
            int signed = signedBalance(environment, subject), balance = Math.max(0, signed);
            var history =
                    jdbc.query(
                            """
SELECT l.id,l.kind,l.coins,l.reason,o.order_number,l.created_at,l.expires_at,o.customer_order_number FROM loyalty_ledger l
LEFT JOIN orders o ON o.id=l.order_id WHERE l.environment=? AND l.subject_id=? ORDER BY l.id DESC LIMIT 100
""",
                            (rs, row) ->
                                    new Entry(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            rs.getInt(3),
                                            rs.getString(4),
                                            rs.getString(5),
                                            rs.getObject(8, Long.class),
                                            rs.getTimestamp(6).toInstant(),
                                            rs.getTimestamp(7) == null
                                                    ? null
                                                    : rs.getTimestamp(7).toInstant()),
                            environment,
                            subject);
            int completed =
                    jdbc.queryForObject(
                            "SELECT COUNT(*)::integer FROM loyalty_qualifying_orders WHERE"
                                    + " environment=? AND subject_id=? AND NOT reversed",
                            Integer.class,
                            environment,
                            subject);
            var expiry =
                    lots(environment, subject).stream()
                            .map(Lot::expiry)
                            .filter(Objects::nonNull)
                            .min(Instant::compareTo)
                            .orElse(null);
            var pendingOrders =
                    jdbc.queryForList(
                            """
SELECT o.subtotal,o.loyalty_discount,o.rebate_discount_amount,o.loyalty_eligible_subtotal,o.loyalty_test_order,o.rebate_code,o.loyalty_earning_rupees_per_coin,o.loyalty_qualifying_minimum
FROM orders o JOIN verified_order_ownership own ON own.order_id=o.id
WHERE own.environment=? AND own.verified_subject_id=? AND o.loyalty_enrolled
 AND o.order_status NOT IN ('PICKED_UP','DELIVERED','CANCELLED','PAYMENT_FAILED')
 AND EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID')
 AND NOT EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status IN ('REFUNDED','REFUND_PENDING'))
""",
                            environment,
                            subject);
            var excludedPromotions = excludedPromotions();
            int pending =
                    pendingOrders.stream()
                            .mapToInt(row -> earning(row, excludedPromotions).coins())
                            .reduce(0, Math::addExact);
            return new Wallet(
                    balance,
                    Math.max(0, -signed),
                    pending,
                    completed,
                    catalogue(balance, subtotal),
                    history,
                    expiry,
                    rules.getRedemptionPercent(),
                    "Earn 1 coin per ₹"
                            + rules.getRupeesPerCoin()
                            + " of eligible products after paid pickup/delivery completion. Minimum"
                            + " net eligible product spend ₹"
                            + rules.getQualifyingSubtotal()
                            + " is required. Tax and fees earn no coins. One reward and one coupon"
                            + " per order; rewards apply first, up to "
                            + rules.getRedemptionPercent()
                            + "% of product subtotal. Coins expire "
                            + rules.getExpiryDays()
                            + " days after your last qualifying completed order. Refunds reverse"
                            + " earned coins; spent reversed coins offset future earnings. No"
                            + " historical-order credits.",
                    policyVersion());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyService.class,
                    "wallet(String,UUID,BigDecimal)");
        }
    }

    /**
     * Previews the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @param subtotal the subtotal
     * @param code the code
     * @return the preview result
     */
    @Transactional
    public Reward preview(String environment, UUID subject, BigDecimal subtotal, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "preview(String,UUID,BigDecimal,String)");
        try {
            if (code == null || code.isBlank()) return null;
            var wallet = wallet(environment, subject, subtotal);
            return wallet.rewards().stream()
                    .filter(r -> r.code().equals(code) && r.eligible())
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    invalid(
                                            "This reward is no longer eligible. Review your coins"
                                                    + " and order subtotal."));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyService.class,
                    "preview(String,UUID,BigDecimal,String)");
        }
    }

    /**
     * Owners the operation.
     *
     * @param orderId the order id
     * @return the owner result
     */
    private Owner owner(long orderId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "owner(long)");
        try {
            return jdbc.query(
                    "SELECT environment,verified_subject_id FROM verified_order_ownership WHERE"
                            + " order_id=?",
                    rs -> rs.next() ? new Owner(rs.getString(1), (UUID) rs.getObject(2)) : null,
                    orderId);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, LoyaltyService.class, "owner(long)");
        }
    }

    /**
     * Called after verified binding, inside order creation. No caller-supplied coin amount is
     * accepted.
     */
    @Transactional
    public void reserve(Order order, String code) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "reserve(Order,String)");
        try {
            if (!enabled()) {
                if (code != null && !code.isBlank())
                    throw invalid(
                            "Rewards are currently unavailable. Review checkout without a reward.");
                return;
            }
            var owner = owner(order.getId());
            if (owner == null) {
                if (code != null && !code.isBlank()) throw invalid("Sign in to use earned coins.");
                return;
            }
            if (!order.isLoyaltyEnrolled()) {
                order.setLoyaltyEarningRupeesPerCoin(rules.getRupeesPerCoin());
                order.setLoyaltyQualifyingMinimum(rules.getQualifyingSubtotal());
                order.setLoyaltyWelcomeCoins(rules.getWelcomeCoins());
                order.setLoyaltyExpiryDays(rules.getExpiryDays());
            }
            lockPolicyForRead();
            order.setLoyaltyEnrolled(true);
            order.setLoyaltyEligibleSubtotal(eligibleSubtotal(order));
            if (code == null || code.isBlank()) return;
            lock(owner.environment(), owner.subject());
            expire(owner.environment(), owner.subject());
            if (jdbc.queryForObject(
                            "SELECT COUNT(*) FROM loyalty_holds WHERE order_id=? AND"
                                    + " state<>'RELEASED'",
                            Integer.class,
                            order.getId())
                    > 0) throw invalid("This order already has a reward reservation.");
            var reward =
                    preview(
                            owner.environment(),
                            owner.subject(),
                            order.getLoyaltyEligibleSubtotal(),
                            code);
            int generation =
                    jdbc.queryForObject(
                            "SELECT COALESCE(MAX(generation),0)+1 FROM loyalty_holds WHERE"
                                    + " order_id=?",
                            Integer.class,
                            order.getId());
            jdbc.update(
                    "INSERT INTO"
                        + " loyalty_holds(order_id,generation,environment,subject_id,reward_code,coins,discount,state)"
                        + " VALUES (?,?,?,?,?,?,?,'RESERVED')",
                    order.getId(),
                    generation,
                    owner.environment(),
                    owner.subject(),
                    code,
                    reward.coins(),
                    reward.discount());
            append(
                    owner.environment(),
                    owner.subject(),
                    "reserve:" + order.getId() + ":" + generation,
                    "RESERVED",
                    -reward.coins(),
                    order.getId(),
                    "Reserved for " + reward.name(),
                    null);
            consumeLots(
                    owner.environment(),
                    owner.subject(),
                    reward.coins(),
                    order.getId(),
                    generation);
            order.setLoyaltyRewardCode(code);
            order.setLoyaltyCoins(reward.coins());
            order.setLoyaltyDiscount(reward.discount());
            PaymentFeePricing.reprice(order);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "reserve(Order,String)");
        }
    }

    /**
     * Consumes lots.
     *
     * @param environment the environment
     * @param subject the subject
     * @param coins the coins
     * @param hold the hold
     * @param generation the generation
     */
    private void consumeLots(
            String environment, UUID subject, int coins, Long hold, int generation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "consumeLots(String,UUID,int,Long,int)");
        try {
            int remaining = coins;
            if (remaining <= 0) return;
            for (var lot : lots(environment, subject)) {
                int take = Math.min(remaining, lot.remaining());
                jdbc.update(
                        "UPDATE loyalty_lots SET remaining=remaining-? WHERE ledger_id=?",
                        take,
                        lot.id());
                if (hold != null)
                    jdbc.update(
                            "INSERT INTO loyalty_hold_lots(order_id,generation,ledger_id,coins)"
                                    + " VALUES (?,?,?,?)",
                            hold,
                            generation,
                            lot.id(),
                            take);
                remaining -= take;
                if (remaining == 0) break;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyService.class,
                    "consumeLots(String,UUID,int,Long,int)");
        }
    }

    /**
     * Reverses order lots.
     *
     * @param owner the owner
     * @param orderId the order id
     * @param coins the coins
     */
    private void reverseOrderLots(Owner owner, long orderId, int coins) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "reverseOrderLots(Owner,long,int)");
        try {
            if (coins <= 0) return;
            var originals =
                    new HashSet<>(
                            jdbc.queryForList(
                                    "SELECT id FROM loyalty_ledger WHERE environment=? AND"
                                            + " subject_id=? AND order_id=? AND kind IN"
                                            + " ('EARNED','WELCOME')",
                                    Long.class,
                                    owner.environment(),
                                    owner.subject(),
                                    orderId));
            int remaining = coins;
            for (var lot : lots(owner.environment(), owner.subject())) {
                if (!originals.contains(lot.id()) && !originals.contains(lot.origin())) continue;
                int take = Math.min(remaining, lot.remaining());
                jdbc.update(
                        "UPDATE loyalty_lots SET remaining=remaining-? WHERE ledger_id=?",
                        take,
                        lot.id());
                remaining -= take;
                if (remaining == 0) break;
            }
            // Already-spent credits offset other available lots; any uncovered debit remains
            // account debt.
            consumeLots(owner.environment(), owner.subject(), remaining, null, 0);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyService.class,
                    "reverseOrderLots(Owner,long,int)");
        }
    }

    /**
     * Releases the operation.
     *
     * @param owner the owner
     * @param orderId the order id
     * @param generation the generation
     */
    private void release(Owner owner, long orderId, int generation) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "release(Owner,long,int)");
        try {
            var allocations =
                    jdbc.query(
                            "SELECT a.ledger_id,a.coins,l.expires_at,l.source_credit_id FROM"
                                + " loyalty_hold_lots a JOIN loyalty_ledger l ON l.id=a.ledger_id"
                                + " WHERE a.order_id=? AND a.generation=?",
                            (rs, n) ->
                                    new Lot(
                                            rs.getLong(1),
                                            rs.getInt(2),
                                            rs.getTimestamp(3) == null
                                                    ? null
                                                    : rs.getTimestamp(3).toInstant(),
                                            (Long) rs.getObject(4)),
                            orderId,
                            generation);
            for (var lot : allocations)
                append(
                        owner.environment(),
                        owner.subject(),
                        "restore:" + orderId + ":" + generation + ":" + lot.id(),
                        "RESTORED",
                        lot.remaining(),
                        orderId,
                        "Reward restored after removal/cancellation/refund",
                        lot.expiry(),
                        lot.origin() == null ? lot.id() : lot.origin());
            jdbc.update(
                    "UPDATE loyalty_holds SET state='RELEASED' WHERE order_id=? AND generation=?",
                    orderId,
                    generation);
            expire(owner.environment(), owner.subject());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "release(Owner,long,int)");
        }
    }

    /**
     * Removes the operation.
     *
     * @param order the order
     */
    @Transactional
    public void remove(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "remove(Order)");
        try {
            var owner = owner(order.getId());
            if (owner == null) return;
            lock(owner.environment(), owner.subject());
            for (int generation :
                    jdbc.queryForList(
                            "SELECT generation FROM loyalty_holds WHERE order_id=? AND"
                                    + " state='RESERVED'",
                            Integer.class,
                            order.getId())) release(owner, order.getId(), generation);
            order.setLoyaltyRewardCode(null);
            order.setLoyaltyCoins(0);
            order.setLoyaltyDiscount(BigDecimal.ZERO);
            PaymentFeePricing.reprice(order);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, LoyaltyService.class, "remove(Order)");
        }
    }

    /**
     * Orders wallet.
     *
     * @param order the order
     * @return the order wallet result
     */
    @Transactional
    public Wallet orderWallet(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "orderWallet(Order)");
        try {
            var owner = owner(order.getId());
            if (owner == null)
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Verified owner sign-in required.");
            var wallet =
                    wallet(
                            owner.environment(),
                            owner.subject(),
                            order.getLoyaltyEligibleSubtotal());
            return new Wallet(
                    wallet.balance(),
                    wallet.debt(),
                    wallet.pendingCoins(),
                    wallet.completedOrders(),
                    catalogue(
                            wallet.balance() + order.getLoyaltyCoins(),
                            order.getLoyaltyEligibleSubtotal()),
                    wallet.history(),
                    wallet.nextExpiry(),
                    wallet.maximumRedemptionPercent(),
                    wallet.terms(),
                    wallet.policyVersion());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "orderWallet(Order)");
        }
    }

    /** Payment totals must never use a released/expired hold, even after rollout is disabled. */
    @Transactional
    public void verifyPayment(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "verifyPayment(Order)");
        try {
            if (order.getLoyaltyCoins() == 0) return;
            lockPolicyForRead();
            var states =
                    jdbc.queryForList(
                            "SELECT state FROM loyalty_holds WHERE order_id=? AND"
                                    + " state<>'RELEASED'",
                            String.class,
                            order.getId());
            if (states.size() != 1 || !states.getFirst().equals("RESERVED"))
                throw invalid("This reward reservation is no longer payable. Review your order.");
            BigDecimal currentEligible = eligibleSubtotal(order);
            boolean eligible =
                    Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM loyalty_rewards r WHERE r.code=?"
                                            + " AND r.active AND r.coins=? AND r.discount=? AND"
                                            + " r.minimum_subtotal<=?)",
                                    Boolean.class,
                                    order.getLoyaltyRewardCode(),
                                    order.getLoyaltyCoins(),
                                    order.getLoyaltyDiscount(),
                                    currentEligible));
            if (!eligible || order.getLoyaltyDiscount().compareTo(cap(currentEligible)) > 0)
                throw invalid("Your reward eligibility changed. Review the reward before payment.");
            var owner = owner(order.getId());
            boolean expired =
                    Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM loyalty_hold_lots h JOIN"
                                        + " loyalty_ledger l ON l.id=h.ledger_id JOIN"
                                        + " loyalty_accounts a ON a.environment=l.environment AND"
                                        + " a.subject_id=l.subject_id JOIN loyalty_holds hold ON"
                                        + " hold.order_id=h.order_id AND"
                                        + " hold.generation=h.generation WHERE h.order_id=? AND"
                                        + " hold.state='RESERVED' AND"
                                        + " GREATEST(l.expires_at,a.last_qualifying_activity+(l.expiry_days*INTERVAL"
                                        + " '1 day'))<=?)",
                                    Boolean.class,
                                    order.getId(),
                                    timestamp(now())));
            if (owner == null || expired)
                throw invalid("Your reserved coins expired. Review checkout without this reward.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "verifyPayment(Order)");
        }
    }

    /** Immutable adjustment data contract. */
    public record Adjustment(UUID key, int coins, String reason) {}

    /** Immutable adjusted data contract. */
    public record Adjusted(long ledgerId, int oldBalance, int newBalance) {}

    /**
     * Adjusts the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @param request the request
     * @param staffId the staff id
     * @return the adjust result
     */
    @Transactional
    public Adjusted adjust(String environment, UUID subject, Adjustment request, long staffId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "adjust(String,UUID,Adjustment,long)");
        try {
            if (!("DEV".equals(environment) || "PROD".equals(environment))
                    || request == null
                    || request.key() == null
                    || request.coins() == 0
                    || Math.abs((long) request.coins()) > 100000
                    || request.reason() == null
                    || request.reason().trim().length() < 8
                    || request.reason().length() > 300)
                throw new IllegalArgumentException(
                        "An adjustment needs a unique key, a bounded coin amount and a clear reason"
                                + " (8–300 characters).");
            if (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM verified_customer_subjects WHERE"
                                    + " environment=? AND id=?)",
                            Boolean.class,
                            environment,
                            subject)))
                throw new IllegalArgumentException(
                        "Verified customer subject does not exist in this environment.");
            lock(environment, subject);
            expire(environment, subject);
            var old =
                    jdbc.query(
                            "SELECT a.ledger_id,a.old_balance,a.new_balance,l.coins,a.reason FROM"
                                + " loyalty_adjustment_audit a JOIN loyalty_ledger l ON"
                                + " l.id=a.ledger_id WHERE l.environment=? AND l.subject_id=? AND"
                                + " l.event_key=?",
                            (rs, n) -> {
                                if (rs.getInt(4) != request.coins()
                                        || !rs.getString(5).equals(request.reason().trim()))
                                    throw invalid(
                                            "This adjustment key was already used for different"
                                                    + " details.");
                                return new Adjusted(rs.getLong(1), rs.getInt(2), rs.getInt(3));
                            },
                            environment,
                            subject,
                            "admin:" + request.key());
            if (!old.isEmpty()) return old.getFirst();
            int before = signedBalance(environment, subject);
            if (request.coins() < 0 && before + request.coins() < 0)
                throw invalid("An administrative debit cannot exceed available coins.");
            long id =
                    append(
                            environment,
                            subject,
                            "admin:" + request.key(),
                            "ADJUSTED",
                            request.coins(),
                            null,
                            request.reason().trim(),
                            now().plus(Duration.ofDays(rules.getExpiryDays())));
            if (request.coins() < 0) consumeLots(environment, subject, -request.coins(), null, 0);
            int after = signedBalance(environment, subject);
            jdbc.update(
                    "INSERT INTO"
                        + " loyalty_adjustment_audit(ledger_id,staff_id,old_balance,new_balance,reason)"
                        + " VALUES (?,?,?,?,?)",
                    id,
                    staffId,
                    before,
                    after,
                    request.reason().trim());
            return new Adjusted(id, before, after);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyService.class,
                    "adjust(String,UUID,Adjustment,long)");
        }
    }

    /** Immutable earning data contract. */
    private record Earning(BigDecimal eligible, BigDecimal minimum, int coins) {}

    /**
     * Excludeds promotions.
     *
     * @return the excluded promotions result
     */
    private Set<String> excludedPromotions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "excludedPromotions()");
        try {
            return new HashSet<>(
                    jdbc.queryForList("SELECT code FROM loyalty_excluded_rebates", String.class));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyService.class, "excludedPromotions()");
        }
    }

    /** One calculation for paid pending estimates and completed-order credits. */
    private Earning earning(Map<String, Object> row, Set<String> excludedPromotions) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "earning(Map<String,Object>,Set<String>)");
        try {
            BigDecimal subtotal = (BigDecimal) row.get("subtotal"),
                    discount = (BigDecimal) row.get("loyalty_discount"),
                    coupon = (BigDecimal) row.get("rebate_discount_amount");
            BigDecimal eligibleBase =
                    ((BigDecimal) row.get("loyalty_eligible_subtotal"))
                            .subtract(discount)
                            .max(BigDecimal.ZERO);
            BigDecimal afterReward = subtotal.subtract(discount).max(BigDecimal.ZERO);
            BigDecimal allocatedCoupon =
                    afterReward.signum() == 0
                            ? BigDecimal.ZERO
                            : (coupon == null ? BigDecimal.ZERO : coupon)
                                    .multiply(eligibleBase)
                                    .divide(afterReward, 2, RoundingMode.HALF_UP);
            BigDecimal eligible = eligibleBase.subtract(allocatedCoupon).max(BigDecimal.ZERO);
            if (Boolean.TRUE.equals(row.get("loyalty_test_order"))
                    || excludedPromotions.contains(row.get("rebate_code")))
                eligible = BigDecimal.ZERO;
            BigDecimal minimum = (BigDecimal) row.get("loyalty_qualifying_minimum");
            int coins =
                    eligible.compareTo(minimum) < 0
                            ? 0
                            : eligible.divideToIntegralValue(
                                            (BigDecimal) row.get("loyalty_earning_rupees_per_coin"))
                                    .intValueExact();
            return new Earning(eligible, minimum, coins);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyService.class,
                    "earning(Map<String,Object>,Set<String>)");
        }
    }

    /**
     * Authoritative terminal order/payment state, not a customer callback. Idempotent under
     * retries.
     */
    @Transactional
    public void reconcile(long orderId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyService.class, "reconcile(long)");
        try {
            jdbc.queryForList("SELECT id FROM orders WHERE id=? FOR UPDATE", orderId);
            var owner = owner(orderId);
            if (owner == null) return;
            lock(owner.environment(), owner.subject());
            expire(owner.environment(), owner.subject());
            var rows =
                    jdbc.queryForList(
                            """
SELECT o.order_status,o.subtotal,o.loyalty_discount,o.rebate_discount_amount,o.loyalty_enrolled,o.loyalty_eligible_subtotal,o.loyalty_test_order,o.rebate_code,o.loyalty_earning_rupees_per_coin,o.loyalty_qualifying_minimum,o.loyalty_welcome_coins,o.loyalty_expiry_days,
 EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID') AS paid,
 EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='REFUNDED') AS refunded
FROM orders o WHERE o.id=?
""",
                            orderId);
            if (rows.isEmpty()) return;
            var row = rows.getFirst();
            String status = (String) row.get("order_status");
            boolean refunded = Boolean.TRUE.equals(row.get("refunded")),
                    paid = Boolean.TRUE.equals(row.get("paid"));
            boolean cancelled = "CANCELLED".equals(status) || "PAYMENT_FAILED".equals(status);
            var holds =
                    jdbc.queryForList(
                            "SELECT generation,state FROM loyalty_holds WHERE order_id=? AND"
                                    + " state<>'RELEASED'",
                            orderId);
            for (var hold : holds) {
                int generation = (Integer) hold.get("generation");
                String state = (String) hold.get("state");
                if (cancelled || refunded) release(owner, orderId, generation);
                else if (paid && state.equals("RESERVED")) {
                    append(
                            owner.environment(),
                            owner.subject(),
                            "consume:" + orderId + ":" + generation,
                            "CONSUMED",
                            0,
                            orderId,
                            "Payment confirmed; reserved coins consumed",
                            null);
                    jdbc.update(
                            "UPDATE loyalty_holds SET state='CONSUMED' WHERE order_id=? AND"
                                    + " generation=?",
                            orderId,
                            generation);
                }
            }
            if (refunded || cancelled) {
                jdbc.update(
                        "UPDATE loyalty_qualifying_orders SET reversed=TRUE WHERE order_id=?",
                        orderId);
                jdbc.update(
                        "UPDATE loyalty_accounts SET last_qualifying_activity=(SELECT"
                                + " MAX(completed_at) FROM loyalty_qualifying_orders WHERE"
                                + " environment=? AND subject_id=? AND NOT reversed) WHERE"
                                + " environment=? AND subject_id=?",
                        owner.environment(),
                        owner.subject(),
                        owner.environment(),
                        owner.subject());
                if (exists(owner.environment(), owner.subject(), "reverse:" + orderId)) return;
                int earned =
                        jdbc.queryForObject(
                                "SELECT COALESCE(SUM(coins),0)::integer FROM loyalty_ledger WHERE"
                                    + " environment=? AND subject_id=? AND order_id=? AND kind IN"
                                    + " ('EARNED','WELCOME')",
                                Integer.class,
                                owner.environment(),
                                owner.subject(),
                                orderId);
                int expired =
                        jdbc.queryForObject(
                                "SELECT COALESCE(SUM(-e.coins),0)::integer FROM loyalty_ledger e"
                                    + " JOIN loyalty_ledger original ON"
                                    + " e.source_credit_id=original.id AND e.kind='EXPIRED' WHERE"
                                    + " original.environment=? AND original.subject_id=? AND"
                                    + " original.order_id=? AND original.kind IN"
                                    + " ('EARNED','WELCOME') AND e.environment=original.environment"
                                    + " AND e.subject_id=original.subject_id",
                                Integer.class,
                                owner.environment(),
                                owner.subject(),
                                orderId);
                earned = Math.max(0, earned - expired);
                {
                    append(
                            owner.environment(),
                            owner.subject(),
                            "reverse:" + orderId,
                            "REVERSED",
                            -earned,
                            orderId,
                            "Earned coins reversed after cancellation/refund",
                            null);
                    reverseOrderLots(owner, orderId, earned);
                }
                return;
            }
            if (!Boolean.TRUE.equals(row.get("loyalty_enrolled"))
                    || !paid
                    || !("PICKED_UP".equals(status) || "DELIVERED".equals(status))) return;
            var earning = earning(row, excludedPromotions());
            BigDecimal eligible = earning.eligible(), minimum = earning.minimum();
            int earned = earning.coins();
            int welcome = (Integer) row.get("loyalty_welcome_coins"),
                    expiryDays = (Integer) row.get("loyalty_expiry_days");
            if (!exists(owner.environment(), owner.subject(), "earned:" + orderId)
                    && eligible.compareTo(minimum) >= 0) {
                jdbc.update(
                        "INSERT INTO"
                            + " loyalty_qualifying_orders(order_id,environment,subject_id,eligible_subtotal,completed_at)"
                            + " VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
                        orderId,
                        owner.environment(),
                        owner.subject(),
                        eligible,
                        timestamp(now()));
                jdbc.update(
                        "UPDATE loyalty_accounts SET last_qualifying_activity=? WHERE environment=?"
                                + " AND subject_id=?",
                        timestamp(now()),
                        owner.environment(),
                        owner.subject());
            }
            append(
                    owner.environment(),
                    owner.subject(),
                    "earned:" + orderId,
                    "EARNED",
                    earned,
                    orderId,
                    "Paid completed order: eligible product spend ₹" + eligible,
                    now().plus(Duration.ofDays(expiryDays)),
                    null,
                    expiryDays);
            if (welcome > 0
                    && eligible.compareTo(minimum) >= 0
                    && !exists(owner.environment(), owner.subject(), "welcome"))
                append(
                        owner.environment(),
                        owner.subject(),
                        "welcome",
                        "WELCOME",
                        welcome,
                        orderId,
                        "First qualifying completed order welcome bonus",
                        now().plus(Duration.ofDays(expiryDays)),
                        null,
                        expiryDays);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, LoyaltyService.class, "reconcile(long)");
        }
    }
}
