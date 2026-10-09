package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

/** Coordinates occasion production readiness operations. */
@Service
@RequiredArgsConstructor
public class OccasionProductionReadinessService {

    private final JdbcTemplate jdbc;

    private final EnhancementProperties features;

    /**
     * Records occasion production readiness data.
     *
     * <p>Reads {@code occasion_enquiries}, {@code occasion_production_allocations}, {@code orders}.
     *
     * <p>Writes {@code OF}, {@code occasion_production_allocations}, {@code
     * occasion_production_readiness_events}.
     *
     * @param environment the environment supplied to this method
     * @param branchId the branch id supplied to this method
     * @param enquiry the enquiry supplied to this method
     * @param productId the product id supplied to this method
     * @param quantity the quantity supplied to this method
     * @param revision the revision supplied to this method
     * @param actor the actor supplied to this method
     * @throws ResponseStatusException when the method rejects the request with {@code Enter a valid
     *     actual ready quantity.}; {@code Ready quantity must fit the approved quantity and unit.}
     */
    @Transactional
    public void record(
            ConsentEnvironment environment,
            long branchId,
            UUID enquiry,
            long productId,
            BigDecimal quantity,
            long revision,
            String actor) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionProductionReadinessService.class,
                        "record(ConsentEnvironment,long,UUID,long,BigDecimal,long,String)");
        try {
            if (!features.isOccasionBulkProduction())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (quantity == null || quantity.signum() < 0 || quantity.scale() > 3 || revision < 0)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Enter a valid actual ready quantity.");
            // Keep lock order identical to the standard READY transition: order, then production
            // rows.
            String status =
                    jdbc.query(
                            """
SELECT o.order_status FROM orders o JOIN occasion_enquiries e ON e.order_id = o.id
WHERE e.id = ? AND e.environment = ? AND e.branch_id = ? AND e.status = 'CONFIRMED'
FOR UPDATE OF o
""",
                            rs -> rs.next() ? rs.getString(1) : null,
                            enquiry,
                            environment.name(),
                            branchId);
            if (status == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (!"PREPARING".equals(status))
                throw conflict(
                        "Record physical readiness while this confirmed order is preparing.");
            var line =
                    jdbc.query(
                            """
SELECT quantity, ready_quantity, readiness_revision, unit FROM occasion_production_allocations
WHERE enquiry_id = ? AND product_id = ? AND state = 'COMMITTED' FOR UPDATE
""",
                            rs ->
                                    rs.next()
                                            ? new Object[] {
                                                rs.getBigDecimal(1),
                                                rs.getBigDecimal(2),
                                                rs.getLong(3),
                                                rs.getString(4)
                                            }
                                            : null,
                            enquiry,
                            productId);
            if (line == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if ((long) line[2] != revision)
                throw conflict("Another staff member updated readiness. Refresh before saving.");
            if (quantity.compareTo((BigDecimal) line[0]) > 0
                    || "PIECE".equals(line[3]) && quantity.stripTrailingZeros().scale() > 0)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Ready quantity must fit the approved quantity and unit.");
            if (quantity.compareTo((BigDecimal) line[1]) == 0) return;
            jdbc.update(
                    """
UPDATE occasion_production_allocations SET ready_quantity = ?, readiness_revision = readiness_revision + 1,
    updated_at = CURRENT_TIMESTAMP WHERE enquiry_id = ? AND product_id = ?
""",
                    quantity,
                    enquiry,
                    productId);
            jdbc.update(
                    """
INSERT INTO occasion_production_readiness_events(enquiry_id, product_id, previous_quantity, ready_quantity, actor)
VALUES (?, ?, ?, ?, ?)
""",
                    enquiry,
                    productId,
                    line[1],
                    quantity,
                    actor);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionProductionReadinessService.class,
                    "record(ConsentEnvironment,long,UUID,long,BigDecimal,long,String)");
        }
    }

    /**
     * Always protect existing dedicated commitments, including during a flag rollback.
     *
     * @param orderId the order id
     */
    @Transactional
    public void requireReady(long orderId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionProductionReadinessService.class, "requireReady(long)");
        try {
            jdbc.queryForList("SELECT id FROM orders WHERE id = ? FOR UPDATE", orderId);
            var lines =
                    jdbc.query(
                            """
SELECT p.state, p.quantity, p.ready_quantity FROM occasion_production_allocations p
JOIN occasion_enquiries e ON e.id = p.enquiry_id WHERE e.order_id = ? ORDER BY p.product_id
FOR UPDATE OF p
""",
                            (rs, row) ->
                                    "COMMITTED".equals(rs.getString(1))
                                            && rs.getBigDecimal(3).compareTo(rs.getBigDecimal(2))
                                                    == 0,
                            orderId);
            if (lines.stream().anyMatch(ready -> !ready))
                throw conflict(
                        "Record the full actual ready quantity for every bulk item before marking"
                                + " this order ready for pickup.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionProductionReadinessService.class,
                    "requireReady(long)");
        }
    }

    /**
     * Creates an HTTP CONFLICT exception using the supplied rejection reason.
     *
     * @param message the message supplied to this method
     * @return the {@code ResponseStatusException} result
     */
    private static ResponseStatusException conflict(String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionProductionReadinessService.class, "conflict(String)");
        try {
            return new ResponseStatusException(HttpStatus.CONFLICT, message);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionProductionReadinessService.class,
                    "conflict(String)");
        }
    }
}
