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
import java.time.*;
import java.util.*;

/** Backend occasion production workspace contract and implementation. */
@Service
@RequiredArgsConstructor
public class OccasionProductionWorkspace {

    private final JdbcTemplate jdbc;

    private final Clock clock;

    private final EnhancementProperties features;

    /**
     * Immutable product data contract.
     *
     * @param productId the product id
     * @param name the name
     * @param requestedPieces the requested pieces
     * @param requestedGrams the requested grams
     * @param committedPieces the committed pieces
     * @param committedGrams the committed grams
     * @param approvedPieces the approved pieces
     * @param approvedGrams the approved grams
     * @param readyPieces the ready pieces
     * @param readyGrams the ready grams
     * @param approvalToken the approval token
     */
    public record Product(
            long productId,
            String name,
            BigDecimal requestedPieces,
            BigDecimal requestedGrams,
            BigDecimal committedPieces,
            BigDecimal committedGrams,
            BigDecimal approvedPieces,
            BigDecimal approvedGrams,
            BigDecimal readyPieces,
            BigDecimal readyGrams,
            String approvalToken) {}

    /**
     * Immutable day data contract.
     *
     * @param date the date
     * @param orderCount the order count
     * @param needsReview the needs review
     * @param committedOrders the committed orders
     * @param products the products
     */
    public record Day(
            LocalDate date,
            int orderCount,
            int needsReview,
            int committedOrders,
            List<Product> products) {}

    /**
     * Immutable week data contract.
     *
     * @param today the today
     * @param days the days
     */
    public record Week(LocalDate today, List<Day> days) {}

    /**
     * Immutable approval data contract.
     *
     * @param token the token
     */
    public record Approval(String token) {}

    /**
     * Returns week information for occasion production workspace.
     *
     * @param env the env supplied to this method
     * @param branch the branch supplied to this method
     * @param from the from supplied to this method
     * @return the {@code Week} result
     */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Week week(ConsentEnvironment env, long branch, LocalDate from) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionProductionWorkspace.class,
                        "week(ConsentEnvironment,long,LocalDate)");
        try {
            if (from == null) from = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
            if (from.isBefore(
                            LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).minusYears(2))
                    || from.isAfter(
                            LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(365)))
                invalid("Choose a planning week within the supported dates.");
            var days = new ArrayList<Day>();
            for (int n = 0; n < 7; n++) days.add(day(env, branch, from.plusDays(n)));
            return new Week(
                    LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))), List.copyOf(days));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionProductionWorkspace.class,
                    "week(ConsentEnvironment,long,LocalDate)");
        }
    }

    /**
     * Immutable calendar day data contract.
     *
     * @param date the date
     * @param orderCount the order count
     * @param needsReview the needs review
     * @param committedOrders the committed orders
     */
    public record CalendarDay(
            LocalDate date, int orderCount, int needsReview, int committedOrders) {}

    /**
     * Immutable month data contract.
     *
     * @param today the today
     * @param month the month
     * @param days the days
     */
    public record Month(LocalDate today, YearMonth month, List<CalendarDay> days) {}

    /**
     * Returns month information for occasion production workspace.
     *
     * <p>Reads {@code occasion_enquiries}.
     *
     * @param env the env supplied to this method
     * @param branch the branch supplied to this method
     * @param month the month supplied to this method
     * @return the {@code Month} result
     */
    @Transactional(readOnly = true)
    public Month month(ConsentEnvironment env, long branch, YearMonth month) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionProductionWorkspace.class,
                        "month(ConsentEnvironment,long,YearMonth)");
        try {
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
            if (month == null) month = YearMonth.from(today);
            if (month.isBefore(YearMonth.from(today.minusYears(2)))
                    || month.isAfter(YearMonth.from(today.plusDays(365))))
                invalid("Choose a calendar month within the supported dates.");
            var counts =
                    jdbc.query(
                            """
SELECT service_date,COUNT(*) total,COUNT(*) FILTER(WHERE status='REQUESTED') review,
 COUNT(*) FILTER(WHERE status IN ('PAID','CONFIRMED')) committed
FROM occasion_enquiries WHERE environment=? AND branch_id=? AND service_date>=? AND service_date<?
GROUP BY service_date
""",
                            (rs, n) ->
                                    new CalendarDay(
                                            rs.getObject(1, LocalDate.class),
                                            rs.getInt(2),
                                            rs.getInt(3),
                                            rs.getInt(4)),
                            env.name(),
                            branch,
                            month.atDay(1),
                            month.plusMonths(1).atDay(1));
            var byDate = new HashMap<LocalDate, CalendarDay>();
            counts.forEach(day -> byDate.put(day.date(), day));
            var days = new ArrayList<CalendarDay>();
            for (int n = 1; n <= month.lengthOfMonth(); n++) {
                LocalDate date = month.atDay(n);
                days.add(byDate.getOrDefault(date, new CalendarDay(date, 0, 0, 0)));
            }
            return new Month(today, month, List.copyOf(days));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionProductionWorkspace.class,
                    "month(ConsentEnvironment,long,YearMonth)");
        }
    }

    /**
     * Returns day information for occasion production workspace.
     *
     * <p>Reads {@code occasion_enquiries}, {@code occasion_enquiry_items}, {@code
     * occasion_production_allocations}, {@code orders}, {@code products}.
     *
     * @param env the env supplied to this method
     * @param branch the branch supplied to this method
     * @param date the date supplied to this method
     * @return the {@code Day} result
     */
    private Day day(ConsentEnvironment env, long branch, LocalDate date) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionProductionWorkspace.class,
                        "day(ConsentEnvironment,long,LocalDate)");
        try {
            var counts =
                    jdbc.query(
                            "SELECT COUNT(*) total,COUNT(*) FILTER(WHERE status='REQUESTED')"
                                + " review,COUNT(*) FILTER(WHERE status IN ('PAID','CONFIRMED'))"
                                + " committed FROM occasion_enquiries WHERE environment=? AND"
                                + " branch_id=? AND service_date=?",
                            rs -> {
                                rs.next();
                                return new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3)};
                            },
                            env.name(),
                            branch,
                            date);
            var products =
                    jdbc.query(
                            """
SELECT p.id,p.name,
 COALESCE(SUM(i.requested_quantity) FILTER(WHERE i.unit='PIECE' AND e.status IN ('REQUESTED','QUOTED')),0) requested_pieces,
 COALESCE(SUM(CASE WHEN i.unit='GRAM' THEN i.requested_quantity ELSE i.supplemental_grams END) FILTER(WHERE e.status IN ('REQUESTED','QUOTED')),0) requested_grams,
 COALESCE(SUM(a.quantity) FILTER(WHERE a.unit='PIECE'),0) committed_pieces,
 COALESCE(SUM(a.quantity) FILTER(WHERE a.unit='GRAM'),0) committed_grams,
 COALESCE(SUM(a.quantity) FILTER(WHERE a.unit='PIECE' AND a.production_approved_at IS NOT NULL),0) approved_pieces,
 COALESCE(SUM(a.quantity) FILTER(WHERE a.unit='GRAM' AND a.production_approved_at IS NOT NULL),0) approved_grams,
 COALESCE(SUM(a.ready_quantity) FILTER(WHERE a.unit='PIECE'),0) ready_pieces,
 COALESCE(SUM(a.ready_quantity) FILTER(WHERE a.unit='GRAM'),0) ready_grams
FROM occasion_enquiries e JOIN occasion_enquiry_items i ON i.enquiry_id=e.id JOIN products p ON p.id=i.product_id
LEFT JOIN orders o ON o.id=e.order_id
LEFT JOIN occasion_production_allocations a ON a.enquiry_id=e.id AND a.product_id=i.product_id AND a.state='COMMITTED' AND e.status IN ('PAID','CONFIRMED')
WHERE e.environment=? AND e.branch_id=? AND e.service_date=? AND e.status IN ('REQUESTED','QUOTED','PAID','CONFIRMED')
 AND (e.status<>'QUOTED' OR e.quote_expires_at>?) AND (o.id IS NULL OR o.order_status NOT IN ('PICKED_UP','DELIVERED','CANCELLED','REFUNDED'))
GROUP BY p.id,p.name ORDER BY p.name
""",
                            (rs, n) ->
                                    new Product(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            rs.getBigDecimal(3),
                                            rs.getBigDecimal(4),
                                            rs.getBigDecimal(5),
                                            rs.getBigDecimal(6),
                                            rs.getBigDecimal(7),
                                            rs.getBigDecimal(8),
                                            rs.getBigDecimal(9),
                                            rs.getBigDecimal(10),
                                            token(env, branch, date, rs.getLong(1))),
                            env.name(),
                            branch,
                            date,
                            java.sql.Timestamp.from(clock.instant()));
            return new Day(date, counts[0], counts[1], counts[2], products);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionProductionWorkspace.class,
                    "day(ConsentEnvironment,long,LocalDate)");
        }
    }

    /**
     * Returns pending information for occasion production workspace.
     *
     * <p>Reads {@code occasion_enquiries}, {@code occasion_production_allocations}, {@code orders}.
     *
     * @param env the env supplied to this method
     * @param branch the branch supplied to this method
     * @param date the date supplied to this method
     * @param product the product supplied to this method
     * @return the {@code List<Map<String, Object>>} result
     */
    private List<Map<String, Object>> pending(
            ConsentEnvironment env, long branch, LocalDate date, long product) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionProductionWorkspace.class,
                        "pending(ConsentEnvironment,long,LocalDate,long)");
        try {
            return jdbc.queryForList(
                    """
SELECT a.enquiry_id,a.quantity,a.unit,a.updated_at FROM occasion_production_allocations a
JOIN occasion_enquiries e ON e.id=a.enquiry_id LEFT JOIN orders o ON o.id=e.order_id
WHERE e.environment=? AND e.branch_id=? AND e.service_date=? AND a.product_id=?
 AND e.status IN ('PAID','CONFIRMED') AND a.state='COMMITTED' AND a.production_approved_at IS NULL
 AND a.ready_quantity=0 AND (o.id IS NULL OR o.order_status='CONFIRMED') ORDER BY a.enquiry_id
""",
                    env.name(),
                    branch,
                    date,
                    product);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionProductionWorkspace.class,
                    "pending(ConsentEnvironment,long,LocalDate,long)");
        }
    }

    /**
     * Returns token information for occasion production workspace.
     *
     * @param env the env supplied to this method
     * @param branch the branch supplied to this method
     * @param date the date supplied to this method
     * @param product the product supplied to this method
     * @return the {@code String} result
     */
    private String token(ConsentEnvironment env, long branch, LocalDate date, long product) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionProductionWorkspace.class,
                        "token(ConsentEnvironment,long,LocalDate,long)");
        try {
            var rows = pending(env, branch, date, product);
            if (rows.isEmpty()) return null;
            try {
                return HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                (env + ":" + branch + ":" + date + ":" + product
                                                                + ":" + rows)
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)));
            } catch (java.security.NoSuchAlgorithmException error) {
                throw new IllegalStateException(error);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionProductionWorkspace.class,
                    "token(ConsentEnvironment,long,LocalDate,long)");
        }
    }

    /**
     * Approves occasion production workspace data and returns the {@code int} result.
     *
     * <p>Reads {@code occasion_enquiries}.
     *
     * <p>Writes {@code occasion_enquiry_events}, {@code occasion_production_allocations}.
     *
     * @param env the env supplied to this method
     * @param branch the branch supplied to this method
     * @param date the date supplied to this method
     * @param product the product supplied to this method
     * @param token the token supplied to this method
     * @param actor the actor supplied to this method
     * @return the {@code int} result
     * @throws ResponseStatusException when the method rejects the request with {@code Production
     *     commitments changed. Refresh and review the new totals.}
     */
    @Transactional
    public int approve(
            ConsentEnvironment env,
            long branch,
            LocalDate date,
            long product,
            String token,
            String actor) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionProductionWorkspace.class,
                        "approve(ConsentEnvironment,long,LocalDate,long,String,String)");
        try {
            if (!features.isOccasionBulkProduction())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            jdbc.queryForList(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                    env + ":" + branch + ":" + date + ":" + product);
            jdbc.queryForList(
                    "SELECT id FROM occasion_enquiries WHERE environment=? AND branch_id=? AND"
                            + " service_date=? AND status IN ('PAID','CONFIRMED') ORDER BY id FOR"
                            + " UPDATE",
                    env.name(),
                    branch,
                    date);
            String current = token(env, branch, date, product);
            if (current == null) return 0;
            if (!current.equals(token))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Production commitments changed. Refresh and review the new totals.");
            int count = 0;
            for (var row : pending(env, branch, date, product)) {
                count +=
                        jdbc.update(
                                "UPDATE occasion_production_allocations SET"
                                    + " production_approved_at=?,production_approved_by=? WHERE"
                                    + " enquiry_id=? AND product_id=? AND production_approved_at IS"
                                    + " NULL",
                                java.sql.Timestamp.from(clock.instant()),
                                actor,
                                row.get("enquiry_id"),
                                product);
                jdbc.update(
                        "INSERT INTO"
                            + " occasion_enquiry_events(enquiry_id,actor,from_status,to_status,detail)"
                            + " SELECT id,?,status,status,? FROM occasion_enquiries WHERE id=?",
                        actor,
                        "Dedicated kitchen production approved: product "
                                + product
                                + ", "
                                + row.get("quantity")
                                + " "
                                + row.get("unit"),
                        row.get("enquiry_id"));
            }
            jdbc.update(
                    "INSERT INTO staff_order_alert_reads(event_id,staff_id) SELECT e.id,u.id FROM"
                        + " staff_order_alerts e JOIN staff_users u ON TRUE JOIN occasion_enquiries"
                        + " oe ON oe.id=e.enquiry_id WHERE e.environment=? AND oe.branch_id=? AND"
                        + " oe.service_date=? AND e.kind='OCCASION_ADVANCE_PAID' AND NOT"
                        + " EXISTS(SELECT 1 FROM occasion_production_allocations a WHERE"
                        + " a.enquiry_id=oe.id AND a.state='COMMITTED' AND a.production_approved_at"
                        + " IS NULL AND a.ready_quantity=0) AND "
                            + com.gokulsweets.restaurant.staff.notification.StaffOrderAlerts
                                    .ELIGIBLE
                            + " ON CONFLICT DO NOTHING",
                    env.name(),
                    branch,
                    date);
            return count;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionProductionWorkspace.class,
                    "approve(ConsentEnvironment,long,LocalDate,long,String,String)");
        }
    }

    /**
     * Rejects the request with an HTTP BAD_REQUEST response.
     *
     * @param message the message supplied to this method
     */
    private static void invalid(String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionProductionWorkspace.class, "invalid(String)");
        try {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionProductionWorkspace.class,
                    "invalid(String)");
        }
    }
}
