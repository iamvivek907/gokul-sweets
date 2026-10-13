package com.gokulsweets.restaurant.payment.routing;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.staff.StaffUser;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/** Stores the global routing decision and bounded owner switch history. */
@Repository
@RequiredArgsConstructor
public class PaymentRoutingStore {
    private final JdbcTemplate jdbc;

    /**
     * Routing state; a null provider uses the deployment's bootstrap default.
     *
     * @param provider the explicitly selected provider
     * @param revision the concurrency revision
     */
    public record Selection(PaymentProviderType provider, long revision) {}

    /**
     * Redacted audit entry.
     *
     * @param previousProvider the previous route
     * @param activeProvider the new route
     * @param revision the routing revision
     * @param username the owner name at switch time
     * @param reason the recorded reason
     * @param changedAt the switch instant
     */
    public record Change(
            String previousProvider,
            String activeProvider,
            long revision,
            String username,
            String reason,
            OffsetDateTime changedAt) {}

    /**
     * Reads one routing snapshot; no process cache delays a committed switch.
     *
     * @return the routing snapshot
     */
    public Selection read() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRoutingStore.class, "read()");
        try {
            return read(false);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, PaymentRoutingStore.class, "read()");
        }
    }

    /**
     * Locks the singleton until the caller's switch transaction commits.
     *
     * @return the locked routing snapshot
     */
    public Selection lock() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRoutingStore.class, "lock()");
        try {
            return read(true);
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, PaymentRoutingStore.class, "lock()");
        }
    }

    /**
     * Reads and optionally locks the singleton.
     *
     * @param lock whether to hold a row lock
     * @return the selection
     */
    private Selection read(boolean lock) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRoutingStore.class, "read(boolean)");
        try {

            return jdbc.queryForObject(
                    "SELECT active_provider, revision FROM payment_provider_routing WHERE singleton"
                            + " = TRUE"
                            + (lock ? " FOR UPDATE" : ""),
                    (rs, row) ->
                            new Selection(
                                    rs.getString(1) == null
                                            ? null
                                            : PaymentProviderType.valueOf(rs.getString(1)),
                                    rs.getLong(2)));

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PaymentRoutingStore.class, "read(boolean)");
        }
    }

    /**
     * Saves the route and its audit entry within the same transaction.
     *
     * @param previous the previous provider
     * @param selected the selected provider
     * @param revision the new revision
     * @param actor the owner
     * @param reason the switch reason
     */
    public void save(
            PaymentProviderType previous,
            PaymentProviderType selected,
            long revision,
            StaffUser actor,
            String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        PaymentRoutingStore.class,
                        "save(PaymentProviderType,PaymentProviderType,long,StaffUser,String)");
        try {

            jdbc.update(
                    "UPDATE payment_provider_routing SET active_provider = ?, revision = ?,"
                            + " changed_at = CURRENT_TIMESTAMP WHERE singleton = TRUE",
                    selected.name(),
                    revision);
            jdbc.update(
                    "INSERT INTO payment_provider_switch_audit(previous_provider, active_provider,"
                        + " revision, staff_id, staff_username, reason) VALUES (?, ?, ?, ?, ?, ?)",
                    previous.name(),
                    selected.name(),
                    revision,
                    actor.getId(),
                    actor.getUsername(),
                    reason);

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    PaymentRoutingStore.class,
                    "save(PaymentProviderType,PaymentProviderType,long,StaffUser,String)");
        }
    }

    /**
     * Returns at most twenty most recent switches.
     *
     * @return bounded audit history
     */
    public List<Change> history() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PaymentRoutingStore.class, "history()");
        try {

            return jdbc.query(
                    "SELECT previous_provider, active_provider, revision, staff_username, reason,"
                        + " changed_at FROM payment_provider_switch_audit ORDER BY id DESC LIMIT"
                        + " 20",
                    (rs, row) ->
                            new Change(
                                    rs.getString(1),
                                    rs.getString(2),
                                    rs.getLong(3),
                                    rs.getString(4),
                                    rs.getString(5),
                                    rs.getObject(6, OffsetDateTime.class)));

        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, PaymentRoutingStore.class, "history()");
        }
    }
}
