package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Internal reservation primitive. Only a server-created order identity may supply a hold key and
 * fingerprint.
 */
@Service
@RequiredArgsConstructor
public class DeliveryRiderHoldService {

    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    private static final Duration HOLD_TTL = Duration.ofMinutes(15);

    private final EnhancementProperties flags;

    private final DeliveryCapacityService capacity;

    private final JdbcTemplate jdbc;

    private final Clock inventoryClock;

    /**
     * Holds delivery rider hold data and returns the {@code boolean} result.
     *
     * <p>Reads {@code delivery_capacity_windows}, {@code delivery_rider_holds}, {@code
     * delivery_zones}.
     *
     * <p>Writes {@code delivery_capacity_windows}, {@code delivery_rider_holds}.
     *
     * @param holdKey the hold key supplied to this method
     * @param fingerprint the fingerprint supplied to this method
     * @param request the request supplied to this method
     * @param windowId the window id supplied to this method
     * @return the {@code boolean} result
     * @throws IllegalArgumentException when the method rejects the request with {@code Invalid
     *     rider hold request.}
     * @throws IllegalStateException when the method rejects the request with {@code Rider holds are
     *     disabled.}
     */
    @Transactional
    public boolean hold(
            String holdKey,
            String fingerprint,
            DeliveryCapacityService.QuoteRequest request,
            long windowId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryRiderHoldService.class,
                        "hold(String,String,DeliveryCapacityService.QuoteRequest,long)");
        try {
            if (!flags.isDeliveryRiderHolds()
                    || !flags.isDeliveryAddressBoundaries()
                    || !capacity.enabled())
                throw new IllegalStateException("Rider holds are disabled.");
            if (holdKey == null
                    || holdKey.isBlank()
                    || holdKey.length() > 100
                    || fingerprint == null
                    || !FINGERPRINT.matcher(fingerprint).matches()
                    || request == null
                    || windowId <= 0)
                throw new IllegalArgumentException("Invalid rider hold request.");
            // Zone configuration acquires this lock before editing its windows or product
            // eligibility.
            var zones =
                    jdbc.queryForList(
                            "SELECT zone_id FROM delivery_capacity_windows WHERE id = ?",
                            Long.class,
                            windowId);
            if (zones.isEmpty()) return false;
            Long zoneId = zones.getFirst();
            if (jdbc.queryForList(
                            "SELECT id FROM delivery_zones WHERE id = ? FOR SHARE",
                            Long.class,
                            zoneId)
                    .isEmpty()) return false;
            if (jdbc.queryForList(
                            "SELECT id FROM delivery_capacity_windows WHERE id = ? AND zone_id = ?"
                                    + " FOR UPDATE",
                            Long.class,
                            windowId,
                            zoneId)
                    .isEmpty()) return false;
            // A retry of the same committed reservation succeeds even when the window is now full.
            var previous =
                    jdbc.query(
                            "SELECT window_id, request_fingerprint, state, expires_at FROM"
                                    + " delivery_rider_holds WHERE hold_key = ?",
                            (rs, row) ->
                                    new Existing(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getTimestamp(4).toInstant()),
                            holdKey);
            if (!previous.isEmpty()) {
                Existing existing = previous.getFirst();
                return existing.windowId() == windowId
                        && existing.fingerprint().equals(fingerprint)
                        && (existing.state().equals("COMMITTED")
                                || existing.state().equals("HELD")
                                        && existing.expiresAt().isAfter(inventoryClock.instant()));
            }
            boolean eligible =
                    capacity.quote(request).provisionalWindows().stream()
                            .anyMatch(w -> w.id() == windowId);
            if (!eligible) return false;
            Instant expiresAt = inventoryClock.instant().plus(HOLD_TTL);
            // Unique key protects concurrent retries across different windows. A lost conflict
            // cannot consume capacity.
            int inserted =
                    jdbc.update(
                            """
INSERT INTO delivery_rider_holds (hold_key, window_id, request_fingerprint, expires_at)
VALUES (?, ?, ?, ?) ON CONFLICT (hold_key) DO NOTHING
""",
                            holdKey,
                            windowId,
                            fingerprint,
                            java.sql.Timestamp.from(expiresAt));
            if (inserted == 0) return false;
            int reserved =
                    jdbc.update(
                            """
UPDATE delivery_capacity_windows SET reserved_count = reserved_count + 1, updated_at = CURRENT_TIMESTAMP
WHERE id = ? AND NOT paused AND reserved_count < rider_capacity
""",
                            windowId);
            if (reserved == 0) {
                jdbc.update("DELETE FROM delivery_rider_holds WHERE hold_key = ?", holdKey);
                return false;
            }
            return true;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryRiderHoldService.class,
                    "hold(String,String,DeliveryCapacityService.QuoteRequest,long)");
        }
    }

    /**
     * Payment acceptance converts a temporary hold into capacity reserved for the order.
     *
     * @param holdKey the hold key
     * @return the operation result
     */
    @Transactional
    public boolean commit(String holdKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryRiderHoldService.class, "commit(String)");
        try {
            return transition(holdKey, "COMMITTED", false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryRiderHoldService.class, "commit(String)");
        }
    }

    /**
     * Cancellation or refund releases either a temporary or a committed reservation exactly once.
     *
     * @param holdKey the hold key
     * @return the operation result
     */
    @Transactional
    public boolean release(String holdKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryRiderHoldService.class, "release(String)");
        try {
            return transition(holdKey, "RELEASED", false);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryRiderHoldService.class, "release(String)");
        }
    }

    /**
     * Called by a future expiry worker; never expires a paid reservation or a renewed hold.
     *
     * @param holdKey the hold key
     * @return the operation result
     */
    @Transactional
    public boolean expire(String holdKey) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryRiderHoldService.class, "expire(String)");
        try {
            return transition(holdKey, "RELEASED", true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryRiderHoldService.class, "expire(String)");
        }
    }

    /**
     * Expireds keys.
     *
     * @param limit the limit
     * @return the expired keys result
     */
    @Transactional(readOnly = true)
    public List<String> expiredKeys(int limit) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryRiderHoldService.class, "expiredKeys(int)");
        try {
            if (limit < 1 || limit > 500)
                throw new IllegalArgumentException("Invalid expiry batch size.");
            return jdbc.queryForList(
                    """
SELECT hold_key FROM delivery_rider_holds WHERE state = 'HELD' AND expires_at <= ?
ORDER BY expires_at LIMIT ?
""",
                    String.class,
                    java.sql.Timestamp.from(inventoryClock.instant()),
                    limit);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryRiderHoldService.class, "expiredKeys(int)");
        }
    }

    /**
     * Changes the state of delivery rider hold data and returns the {@code boolean} result.
     *
     * <p>Reads {@code delivery_capacity_windows}, {@code delivery_rider_holds}.
     *
     * <p>Writes {@code delivery_capacity_windows}, {@code delivery_rider_holds}.
     *
     * @param key the key supplied to this method
     * @param target the target supplied to this method
     * @param expiryOnly the expiry only supplied to this method
     * @return the {@code boolean} result
     * @throws IllegalStateException when the method rejects the request with {@code Rider capacity
     *     accounting is inconsistent.}
     */
    private boolean transition(String key, String target, boolean expiryOnly) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryRiderHoldService.class, "transition(String,String,boolean)");
        try {
            if (key == null || key.isBlank()) return false;
            var windowIds =
                    jdbc.queryForList(
                            "SELECT window_id FROM delivery_rider_holds WHERE hold_key = ?",
                            Long.class,
                            key);
            if (windowIds.isEmpty()) return false;
            long windowId = windowIds.getFirst();
            // Same window-before-hold order as hold(), including simultaneous payment and expiry.
            jdbc.queryForList(
                    "SELECT id FROM delivery_capacity_windows WHERE id = ? FOR UPDATE",
                    Long.class,
                    windowId);
            var rows =
                    jdbc.query(
                            "SELECT state, expires_at FROM delivery_rider_holds WHERE hold_key = ?"
                                    + " FOR UPDATE",
                            (rs, row) -> new State(rs.getString(1), rs.getTimestamp(2).toInstant()),
                            key);
            if (rows.isEmpty()) return false;
            State state = rows.getFirst();
            if (state.name().equals(target)) return true;
            if (state.name().equals("RELEASED")
                    || (target.equals("COMMITTED")
                            && (!state.name().equals("HELD")
                                    || !state.expiresAt().isAfter(inventoryClock.instant()))))
                return false;
            if (expiryOnly
                    && (!state.name().equals("HELD")
                            || state.expiresAt().isAfter(inventoryClock.instant()))) return false;
            jdbc.update(
                    "UPDATE delivery_rider_holds SET state = ?, updated_at = CURRENT_TIMESTAMP"
                            + " WHERE hold_key = ?",
                    target,
                    key);
            if (target.equals("RELEASED")) {
                int updated =
                        jdbc.update(
                                """
UPDATE delivery_capacity_windows SET reserved_count = reserved_count - 1, updated_at = CURRENT_TIMESTAMP
WHERE id = ? AND reserved_count > 0
""",
                                windowId);
                if (updated != 1)
                    throw new IllegalStateException("Rider capacity accounting is inconsistent.");
            }
            return true;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryRiderHoldService.class,
                    "transition(String,String,boolean)");
        }
    }

    /**
     * Immutable existing data contract.
     *
     * @param windowId the window id
     * @param fingerprint the fingerprint
     * @param state the state
     * @param expiresAt the expires at
     */
    private record Existing(long windowId, String fingerprint, String state, Instant expiresAt) {}

    /**
     * Immutable state data contract.
     *
     * @param name the name
     * @param expiresAt the expires at
     */
    private record State(String name, Instant expiresAt) {}
}
