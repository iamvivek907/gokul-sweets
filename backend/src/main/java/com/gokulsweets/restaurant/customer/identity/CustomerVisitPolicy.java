package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.entity.Order;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** Backend customer visit policy contract and implementation. */
@Service
@RequiredArgsConstructor
public class CustomerVisitPolicy {

    private final JdbcTemplate jdbc;

    /**
     * Returns completed information for customer visit policy.
     *
     * <p>Reads {@code orders}, {@code payments}, {@code verified_order_ownership}.
     *
     * @param environment the environment supplied to this method
     * @param subject the subject supplied to this method
     * @return the {@code long} result
     */
    public long completed(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerVisitPolicy.class, "completed(String,UUID)");
        try {
            return jdbc.queryForObject(
                    """
SELECT COUNT(*) FROM verified_order_ownership own JOIN orders o ON o.id=own.order_id
WHERE own.environment=? AND own.verified_subject_id=? AND o.order_status IN ('PICKED_UP','DELIVERED')
  AND EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID')
  AND NOT EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status IN ('REFUND_PENDING','REFUNDED','REFUND_FAILED'))
""",
                    Long.class,
                    environment,
                    subject);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerVisitPolicy.class, "completed(String,UUID)");
        }
    }

    /** Defines the supported selection mode values. */
    public enum SelectionMode {

        /** The preview value. */
        PREVIEW,
        /** The acceptance value. */
        ACCEPTANCE
    }

    /**
     * Offers eligible.
     *
     * @param rebateId the rebate id
     * @param order the order
     * @return the offer eligible result
     */
    public boolean offerEligible(long rebateId, Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerVisitPolicy.class, "offerEligible(long,Order)");
        try {
            return offerEligible(rebateId, order, SelectionMode.PREVIEW);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerVisitPolicy.class,
                    "offerEligible(long,Order)");
        }
    }

    /**
     * Offers eligible.
     *
     * @param rebateId the rebate id
     * @param order the order
     * @param mode the mode
     * @return the offer eligible result
     */
    public boolean offerEligible(long rebateId, Order order, SelectionMode mode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerVisitPolicy.class, "offerEligible(long,Order,SelectionMode)");
        try {
            return eligibleOffers(java.util.List.of(rebateId), order, mode).contains(rebateId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerVisitPolicy.class,
                    "offerEligible(long,Order,SelectionMode)");
        }
    }

    /**
     * One request-local snapshot; never cache eligibility across orders or policy updates.
     *
     * @param rebateIds the rebate ids
     * @param order the order
     * @param mode the mode
     * @return the operation result
     */
    public java.util.Set<Long> eligibleOffers(
            java.util.Collection<Long> rebateIds, Order order, SelectionMode mode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerVisitPolicy.class,
                        "eligibleOffers(java.util.Collection<Long>,Order,SelectionMode)");
        try {
            var ids = rebateIds.stream().distinct().sorted().toList();
            if (ids.isEmpty()) return java.util.Set.of();
            var placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
            // Selection intent is explicit: a preview may have a writable outer wallet transaction.
            if (mode == SelectionMode.ACCEPTANCE) {
                if (!TransactionSynchronizationManager.isActualTransactionActive()
                        || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                    throw new IllegalStateException(
                            "Offer acceptance requires a writable transaction.");
                // Stable parent locks protect even rules not inserted yet.
                jdbc.queryForList(
                        "SELECT id FROM rebates WHERE id IN ("
                                + placeholders
                                + ") ORDER BY id FOR SHARE",
                        Long.class,
                        ids.toArray());
            }
            var minima = new java.util.HashMap<Long, Integer>();
            jdbc.query(
                    "SELECT rebate_id,minimum_completed_orders FROM rebate_visit_rules WHERE"
                            + " rebate_id IN ("
                            + placeholders
                            + ")",
                    (org.springframework.jdbc.core.RowCallbackHandler)
                            rs -> minima.put(rs.getLong(1), rs.getInt(2)),
                    ids.toArray());
            long completedCount =
                    minima.values().stream().anyMatch(minimum -> minimum > 0)
                            ? completedForOrder(order)
                            : 0;
            return ids.stream()
                    .filter(id -> completedCount >= minima.getOrDefault(id, 0))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerVisitPolicy.class,
                    "eligibleOffers(java.util.Collection<Long>,Order,SelectionMode)");
        }
    }

    /**
     * Completeds for order.
     *
     * @param order the order
     * @return the completed for order result
     */
    private long completedForOrder(Order order) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerVisitPolicy.class, "completedForOrder(Order)");
        try {
            // Draft quotes have no order id; only a validated phone-bound owner can qualify.
            if (order.getVerifiedOfferSubject() != null) {
                var subject = order.getVerifiedOfferSubject();
                return completed(subject.environment(), subject.id());
            }
            var owners =
                    order.getId() == null
                            ? java.util.List.<java.util.Map<String, Object>>of()
                            : jdbc.queryForList(
                                    "SELECT environment,verified_subject_id FROM"
                                            + " verified_order_ownership WHERE order_id=?",
                                    order.getId());
            if (owners.isEmpty()) return 0;
            var owner = owners.getFirst();
            return completed(
                    (String) owner.get("environment"), (UUID) owner.get("verified_subject_id"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerVisitPolicy.class,
                    "completedForOrder(Order)");
        }
    }
}
