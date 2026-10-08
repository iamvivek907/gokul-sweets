package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.pickup.service.PickupSlotReservationService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Paid cancellation is an operational decision; refund eligibility/execution remains finance-owned.
 */
@Service
@RequiredArgsConstructor
public class OccasionCancellationService {

    private final JdbcTemplate jdbc;

    private final EnhancementProperties features;

    private final PickupSlotReservationService slots;

    /**
     * Cancels the operation.
     *
     * @param environment the environment
     * @param branch the branch
     * @param enquiry the enquiry
     * @param actor the actor
     * @param reason the reason
     */
    @Transactional
    public void cancel(
            ConsentEnvironment environment,
            long branch,
            UUID enquiry,
            String actor,
            String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionCancellationService.class,
                        "cancel(ConsentEnvironment,long,UUID,String,String)");
        try {
            if (!features.isOccasionBulkProduction())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (reason == null || reason.isBlank() || reason.length() > 500)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Explain the cancellation for customer and finance review.");
            // Existing order/readiness updates acquire the order lock first.
            jdbc.queryForList(
                    """
                    SELECT o.id FROM orders o JOIN occasion_enquiries e ON e.order_id = o.id
                    WHERE e.id = ? AND e.environment = ? AND e.branch_id = ? FOR UPDATE OF o
                    """,
                    enquiry,
                    environment.name(),
                    branch);
            var row =
                    jdbc.query(
                            """
SELECT status, paid_amount, pickup_slot_id, order_id FROM occasion_enquiries
WHERE id = ? AND environment = ? AND branch_id = ? FOR UPDATE
""",
                            rs ->
                                    rs.next()
                                            ? new Object[] {
                                                rs.getString(1),
                                                rs.getBigDecimal(2),
                                                rs.getObject(3, Long.class),
                                                rs.getObject(4, Long.class)
                                            }
                                            : null,
                            enquiry,
                            environment.name(),
                            branch);
            if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if ("CANCELLED".equals(row[0])) return;
            if (!("PAID".equals(row[0]) || "CONFIRMED".equals(row[0]))
                    || ((BigDecimal) row[1]).signum() <= 0)
                throw conflict(
                        "Only a verified paid bulk booking can use this cancellation. Reconcile"
                                + " pending payments first.");
            Long order = (Long) row[3];
            if (order != null) {
                String status =
                        jdbc.query(
                                "SELECT order_status FROM orders WHERE id = ? FOR UPDATE",
                                rs -> rs.next() ? rs.getString(1) : null,
                                order);
                if (!"CONFIRMED".equals(status))
                    throw conflict(
                            "Preparation has started or the order is no longer cancellable here."
                                    + " Contact branch operations and finance.");
            }
            var plans =
                    jdbc.query(
                            """
                            SELECT state, ready_quantity FROM occasion_production_allocations
                            WHERE enquiry_id = ? ORDER BY product_id FOR UPDATE
                            """,
                            (rs, index) ->
                                    "COMMITTED".equals(rs.getString(1))
                                            && rs.getBigDecimal(2).signum() == 0,
                            enquiry);
            if (plans.isEmpty() || plans.stream().anyMatch(valid -> !valid))
                throw conflict(
                        "This booking needs staff review; prepared production cannot be"
                                + " automatically released.");
            if (order != null)
                jdbc.update(
                        "UPDATE orders SET order_status = 'CANCELLED', updated_at ="
                                + " CURRENT_TIMESTAMP WHERE id = ?",
                        order);
            jdbc.update(
                    "UPDATE occasion_production_allocations SET state = 'RELEASED', updated_at ="
                            + " CURRENT_TIMESTAMP WHERE enquiry_id = ?",
                    enquiry);
            if (row[2] != null) slots.releaseNormalCapacity((Long) row[2]);
            // A concurrent/late balance payment remains auditable and goes through the existing
            // late-charge review.
            jdbc.update(
                    "UPDATE occasion_payment_attempts SET status = 'EXPIRED', updated_at ="
                            + " CURRENT_TIMESTAMP WHERE enquiry_id = ? AND status = 'PENDING'",
                    enquiry);
            jdbc.update(
                    "INSERT INTO occasion_cancellation_reviews(enquiry_id, paid_amount, reason,"
                            + " actor) VALUES (?, ?, ?, ?)",
                    enquiry,
                    row[1],
                    reason.trim(),
                    actor);
            jdbc.update(
                    "UPDATE occasion_enquiries SET status = 'CANCELLED', updated_at ="
                            + " CURRENT_TIMESTAMP WHERE id = ?",
                    enquiry);
            jdbc.update(
                    """
INSERT INTO occasion_enquiry_events(enquiry_id, actor, from_status, to_status, detail)
VALUES (?, ?, ?, 'CANCELLED', ?)
""",
                    enquiry,
                    actor,
                    row[0],
                    reason.trim());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionCancellationService.class,
                    "cancel(ConsentEnvironment,long,UUID,String,String)");
        }
    }

    /**
     * Conflicts the operation.
     *
     * @param message the message
     * @return the conflict result
     */
    private static ResponseStatusException conflict(String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionCancellationService.class, "conflict(String)");
        try {
            return new ResponseStatusException(HttpStatus.CONFLICT, message);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionCancellationService.class,
                    "conflict(String)");
        }
    }
}
