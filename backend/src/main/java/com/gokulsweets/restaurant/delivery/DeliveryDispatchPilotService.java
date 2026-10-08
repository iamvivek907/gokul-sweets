package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Pilot dispatch: single order per rider/window, with database enforced capacity and branch
 * isolation.
 */
@Service
@RequiredArgsConstructor
public class DeliveryDispatchPilotService {

    private final EnhancementProperties flags;

    private final StaffAuthorizationService staff;

    private final JdbcTemplate jdbc;

    private final Clock inventoryClock;

    /** Immutable board row data contract. */
    public record BoardRow(
            long orderId,
            String orderNumber,
            long windowId,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            String orderStatus,
            Long riderId,
            String state,
            String customerPhone,
            String address) {}

    /** Immutable rider data contract. */
    public record Rider(long id, String displayName, boolean active) {}

    /** Immutable assignment data contract. */
    public record Assignment(long orderId, long riderId, long windowId, String state) {}

    /**
     * Boards the operation.
     *
     * @param branchId the branch id
     * @return the board result
     */
    @Transactional(readOnly = true)
    public List<BoardRow> board(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryDispatchPilotService.class, "board(long)");
        try {
            requireBranch(branchId);
            return jdbc.query(
                    """
SELECT o.id, o.order_number, w.id, w.service_date, w.starts_at, w.ends_at,
       o.order_status, a.rider_id, a.state, o.customer_phone, o.delivery_address_line
FROM orders o JOIN delivery_capacity_windows w ON w.id = o.delivery_window_id
LEFT JOIN delivery_pilot_assignments a ON a.order_id = o.id
WHERE o.branch_id = ? AND o.fulfillment_type = 'DELIVERY'
  AND w.service_date BETWEEN ? AND ?
  AND (o.order_status IN ('CONFIRMED', 'PREPARING', 'READY_FOR_DELIVERY', 'OUT_FOR_DELIVERY')
      OR (o.order_status = 'DELIVERED' AND a.actual_journey_cost IS NULL))
ORDER BY w.service_date, w.starts_at, o.id
""",
                    (rs, row) ->
                            new BoardRow(
                                    rs.getLong(1),
                                    rs.getString(2),
                                    rs.getLong(3),
                                    rs.getDate(4).toLocalDate(),
                                    rs.getTime(5).toLocalTime(),
                                    rs.getTime(6).toLocalTime(),
                                    rs.getString(7),
                                    (Long) rs.getObject(8),
                                    rs.getString(9),
                                    rs.getString(10),
                                    rs.getString(11)),
                    branchId,
                    Date.valueOf(LocalDate.now(inventoryClock)),
                    Date.valueOf(LocalDate.now(inventoryClock).plusDays(2)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryDispatchPilotService.class, "board(long)");
        }
    }

    /**
     * Riderses the operation.
     *
     * @param branchId the branch id
     * @return the riders result
     */
    @Transactional(readOnly = true)
    public List<Rider> riders(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryDispatchPilotService.class, "riders(long)");
        try {
            requireBranch(branchId);
            return jdbc.query(
                    "SELECT id, display_name, active FROM delivery_pilot_riders WHERE branch_id = ?"
                            + " ORDER BY id",
                    (rs, index) -> new Rider(rs.getLong(1), rs.getString(2), rs.getBoolean(3)),
                    branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryDispatchPilotService.class, "riders(long)");
        }
    }

    /**
     * Adds rider.
     *
     * @param branchId the branch id
     * @param name the name
     * @return the add rider result
     */
    @Transactional
    public Rider addRider(long branchId, String name) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryDispatchPilotService.class, "addRider(long,String)");
        try {
            requireBranch(branchId);
            if (name == null || name.isBlank() || name.length() > 100)
                throw new IllegalArgumentException("Rider name is required.");
            Long id =
                    jdbc.queryForObject(
                            "INSERT INTO delivery_pilot_riders(branch_id, display_name) VALUES (?,"
                                    + " ?) RETURNING id",
                            Long.class,
                            branchId,
                            name.trim());
            return new Rider(id, name.trim(), true);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryDispatchPilotService.class,
                    "addRider(long,String)");
        }
    }

    /**
     * Availability the operation.
     *
     * @param branchId the branch id
     * @param riderId the rider id
     * @param windowId the window id
     * @param available the available
     */
    @Transactional
    public void availability(long branchId, long riderId, long windowId, boolean available) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryDispatchPilotService.class, "availability(long,long,long,boolean)");
        try {
            requireBranch(branchId);
            if (jdbc.queryForObject(
                                    "SELECT count(*) FROM delivery_pilot_riders WHERE id = ? AND"
                                            + " branch_id = ? AND active",
                                    Integer.class,
                                    riderId,
                                    branchId)
                            != 1
                    || jdbc.queryForObject(
                                    """
SELECT count(*) FROM delivery_capacity_windows w JOIN delivery_zones z ON z.id = w.zone_id
WHERE w.id = ? AND z.branch_id = ? AND w.service_date >= ? AND NOT w.paused
""",
                                    Integer.class,
                                    windowId,
                                    branchId,
                                    Date.valueOf(LocalDate.now(inventoryClock)))
                            != 1)
                throw new IllegalArgumentException("Rider or branch window is unavailable.");
            if (!available
                    && jdbc.queryForObject(
                                    """
SELECT count(*) FROM delivery_pilot_assignments
WHERE rider_id = ? AND window_id = ? AND state IN ('ASSIGNED', 'DISPATCHED')
""",
                                    Integer.class,
                                    riderId,
                                    windowId)
                            > 0)
                throw new IllegalStateException(
                        "Resolve this rider's active delivery before removing availability.");
            jdbc.update(
                    """
INSERT INTO delivery_rider_availability(rider_id, window_id, available) VALUES (?, ?, ?)
ON CONFLICT (rider_id, window_id) DO UPDATE SET available = EXCLUDED.available
""",
                    riderId,
                    windowId,
                    available);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryDispatchPilotService.class,
                    "availability(long,long,long,boolean)");
        }
    }

    /**
     * Assigns the operation.
     *
     * @param branchId the branch id
     * @param orderId the order id
     * @param riderId the rider id
     * @return the assign result
     */
    @Transactional
    public Assignment assign(long branchId, long orderId, long riderId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryDispatchPilotService.class, "assign(long,long,long)");
        try {
            requireBranch(branchId);
            var orders =
                    jdbc.query(
                            """
SELECT o.delivery_window_id FROM orders o WHERE o.id = ? AND o.branch_id = ?
    AND o.fulfillment_type = 'DELIVERY' AND o.order_status = 'READY_FOR_DELIVERY'
FOR UPDATE
""",
                            (rs, row) -> rs.getLong(1),
                            orderId,
                            branchId);
            if (orders.isEmpty())
                throw new IllegalStateException("Order is not ready for rider assignment.");
            long windowId = orders.getFirst();
            jdbc.queryForObject(
                    "SELECT id FROM delivery_capacity_windows WHERE id = ? FOR UPDATE",
                    Long.class,
                    windowId);
            var riders =
                    jdbc.query(
                            """
SELECT r.id FROM delivery_pilot_riders r JOIN delivery_rider_availability a ON a.rider_id = r.id
JOIN delivery_capacity_windows w ON w.id = a.window_id
WHERE r.id = ? AND r.branch_id = ? AND r.active AND a.window_id = ? AND a.available
  AND NOT w.paused AND w.service_date >= ? FOR UPDATE OF r
""",
                            (rs, row) -> rs.getLong(1),
                            riderId,
                            branchId,
                            windowId,
                            Date.valueOf(LocalDate.now(inventoryClock)));
            if (riders.isEmpty())
                throw new IllegalStateException("Rider is unavailable for this window.");
            if (jdbc.queryForObject(
                            """
SELECT count(*) FROM delivery_pilot_assignments a JOIN orders o ON o.id = a.order_id
WHERE o.branch_id = ? AND a.window_id = ? AND a.state IN ('ASSIGNED', 'DISPATCHED')
""",
                            Integer.class,
                            branchId,
                            windowId)
                    >= jdbc.queryForObject(
                            "SELECT rider_capacity FROM delivery_capacity_windows WHERE id = ?",
                            Integer.class,
                            windowId))
                throw new IllegalStateException("Branch rider capacity is full.");
            int inserted =
                    jdbc.update(
                            """
INSERT INTO delivery_pilot_assignments(order_id, rider_id, window_id, state)
VALUES (?, ?, ?, 'ASSIGNED') ON CONFLICT (order_id) DO UPDATE SET
    rider_id = EXCLUDED.rider_id, state = 'ASSIGNED', outcome = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE delivery_pilot_assignments.state = 'EXCEPTION'
""",
                            orderId,
                            riderId,
                            windowId);
            if (inserted != 1)
                throw new IllegalStateException(
                        "Order already has a rider; resolve the assignment first.");
            return new Assignment(orderId, riderId, windowId, "ASSIGNED");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryDispatchPilotService.class,
                    "assign(long,long,long)");
        }
    }

    /**
     * Requires assignment.
     *
     * @param orderId the order id
     */
    public void requireAssignment(long orderId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryDispatchPilotService.class, "requireAssignment(long)");
        try {
            if (flags.isDeliveryDispatchPilot()
                    && jdbc.queryForObject(
                                    """
SELECT count(*) FROM delivery_pilot_assignments WHERE order_id = ? AND state = 'ASSIGNED'
""",
                                    Integer.class,
                                    orderId)
                            != 1)
                throw new IllegalStateException("Assign an available rider before dispatch.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryDispatchPilotService.class,
                    "requireAssignment(long)");
        }
    }

    /**
     * Records transition.
     *
     * @param orderId the order id
     * @param state the state
     */
    @Transactional
    public void recordTransition(long orderId, String state) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryDispatchPilotService.class, "recordTransition(long,String)");
        try {
            if (!flags.isDeliveryDispatchPilot()) return;
            jdbc.update(
                    "UPDATE delivery_pilot_assignments SET state = ?, updated_at ="
                            + " CURRENT_TIMESTAMP WHERE order_id = ?",
                    state,
                    orderId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryDispatchPilotService.class,
                    "recordTransition(long,String)");
        }
    }

    /**
     * Exceptions the operation.
     *
     * @param branchId the branch id
     * @param orderId the order id
     * @param reason the reason
     * @param detail the detail
     * @param contacted the contacted
     */
    @Transactional
    public void exception(
            long branchId, long orderId, String reason, String detail, boolean contacted) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryDispatchPilotService.class,
                        "exception(long,long,String,String,boolean)");
        try {
            requireBranch(branchId);
            if (!List.of(
                                    "RIDER_NO_SHOW",
                                    "BAD_ROADS",
                                    "MISSING_ADDRESS",
                                    "FOOD_READY_EARLY",
                                    "NO_SIGNAL",
                                    "FAILED_HANDOFF",
                                    "OTHER")
                            .contains(reason)
                    || detail == null
                    || detail.isBlank()
                    || detail.length() > 300)
                throw new IllegalArgumentException("Choose a reason and explain the issue.");
            var statuses =
                    jdbc.query(
                            "SELECT order_status FROM orders WHERE id = ? AND branch_id = ? AND"
                                    + " fulfillment_type = 'DELIVERY' FOR UPDATE",
                            (rs, row) -> rs.getString(1),
                            orderId,
                            branchId);
            if (statuses.isEmpty()
                    || !List.of("READY_FOR_DELIVERY", "OUT_FOR_DELIVERY")
                            .contains(statuses.getFirst()))
                throw new IllegalStateException(
                        "Delivery exception is unavailable for this order.");
            jdbc.update(
                    "INSERT INTO delivery_dispatch_exceptions(order_id, reason, detail,"
                            + " customer_contacted) VALUES (?, ?, ?, ?)",
                    orderId,
                    reason,
                    detail.trim(),
                    contacted);
            if (statuses.getFirst().equals("READY_FOR_DELIVERY") && reason.equals("RIDER_NO_SHOW"))
                jdbc.update(
                        "UPDATE delivery_pilot_assignments SET state = 'EXCEPTION', outcome = ?,"
                                + " updated_at = CURRENT_TIMESTAMP WHERE order_id = ?",
                        reason,
                        orderId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryDispatchPilotService.class,
                    "exception(long,long,String,String,boolean)");
        }
    }

    /**
     * Completes the operation.
     *
     * @param branchId the branch id
     * @param orderId the order id
     * @param actualJourneyCost the actual journey cost
     * @param outcome the outcome
     */
    @Transactional
    public void complete(
            long branchId, long orderId, BigDecimal actualJourneyCost, String outcome) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryDispatchPilotService.class,
                        "complete(long,long,BigDecimal,String)");
        try {
            requireBranch(branchId);
            if (actualJourneyCost == null
                    || actualJourneyCost.signum() < 0
                    || outcome == null
                    || outcome.isBlank()
                    || outcome.length() > 120)
                throw new IllegalArgumentException("Actual journey cost and outcome are required.");
            int updated =
                    jdbc.update(
                            """
UPDATE delivery_pilot_assignments a SET actual_journey_cost = ?, outcome = ?, updated_at = CURRENT_TIMESTAMP
FROM orders o WHERE a.order_id = o.id AND o.id = ? AND o.branch_id = ?
  AND o.order_status = 'DELIVERED' AND a.state = 'DELIVERED'
""",
                            actualJourneyCost,
                            outcome.trim(),
                            orderId,
                            branchId);
            if (updated != 1)
                throw new IllegalStateException("Only a delivered trip can record an outcome.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryDispatchPilotService.class,
                    "complete(long,long,BigDecimal,String)");
        }
    }

    /**
     * Requires branch.
     *
     * @param branchId the branch id
     */
    private void requireBranch(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryDispatchPilotService.class, "requireBranch(long)");
        try {
            if (!flags.isDeliveryDispatchPilot() || !flags.isDeliveryEconomics())
                throw new IllegalStateException(
                        "Pilot dispatch requires approved delivery economics.");
            staff.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryDispatchPilotService.class,
                    "requireBranch(long)");
        }
    }
}
