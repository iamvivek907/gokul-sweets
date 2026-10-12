package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Owner-scoped, bounded history reads with stable forward and backward cursors. */
@Service
@RequiredArgsConstructor
public class CustomerHistoryQuery {
    private final JdbcTemplate jdbc;
    private final VerifiedOrderOwnership ownership;

    /** Filters for a single cursor page. */
    public record Criteria(
            String before,
            String after,
            int limit,
            String branch,
            String status,
            String query,
            String from,
            String to) {}

    /** Bounded references and cursor navigation availability. */
    public record Page(List<String> references, boolean hasPrevious, boolean hasNext) {}

    /**
     * Reads a bounded page for one verified account.
     *
     * @param environment the identity environment
     * @param subject the verified account
     * @param criteria validated filters and cursor
     * @return references and navigation availability
     */
    public Page read(String environment, UUID subject, Criteria criteria) {
        final long started =
                MethodTiming.start(CustomerHistoryQuery.class, "read(String,UUID,Criteria)");
        try {
            String before = criteria.before(), after = criteria.after();
            if (criteria.limit() < 1 || criteria.limit() > 50 || before != null && after != null)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid history position.");
            String cursor = before != null ? before : after;
            if (cursor != null
                    && (cursor.length() > 100 || !ownership.owns(environment, subject, cursor)))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "History position is no longer available.");
            var args = new ArrayList<Object>();
            args.add(environment);
            args.add(subject);
            var sql =
                    new StringBuilder(
                            "SELECT o.order_number FROM verified_order_ownership own JOIN orders o"
                                    + " ON o.id=own.order_id WHERE own.environment=? AND"
                                    + " own.verified_subject_id=?");
            if (criteria.branch() != null) {
                long branch;
                try {
                    branch = Long.parseLong(criteria.branch());
                } catch (NumberFormatException invalid) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid branch.");
                }
                if (branch <= 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid branch.");
                sql.append(" AND o.branch_id=?");
                args.add(branch);
            }
            String status = criteria.status() == null ? "ALL" : criteria.status();
            sql.append(
                    switch (status) {
                        case "ALL" -> "";
                        case "ACTIVE" ->
                                " AND o.order_status IN"
                                    + " ('PENDING_PAYMENT','CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY','OUT_FOR_DELIVERY')";
                        case "COMPLETED" -> " AND o.order_status IN ('PICKED_UP','DELIVERED')";
                        case "ISSUES" ->
                                " AND o.order_status IN"
                                    + " ('CANCELLED','PAYMENT_FAILED','NO_SHOW','PICKUP_WINDOW_EXPIRED')";
                        default ->
                                throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Invalid history filter.");
                    });
            if (criteria.query() != null && !criteria.query().isBlank()) {
                if (criteria.query().length() > 100)
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Search is too long.");
                String query = criteria.query().trim().toLowerCase(Locale.ROOT);
                sql.append(
                        " AND (POSITION(? IN LOWER(o.order_number))>0 OR POSITION(? IN"
                            + " COALESCE('#'||o.customer_order_number::text,''))>0 OR EXISTS(SELECT"
                            + " 1 FROM order_items i WHERE i.order_id=o.id AND POSITION(? IN"
                            + " LOWER(i.product_name))>0))");
                args.add(query);
                args.add(query);
                args.add(query);
            }
            LocalDate from, to;
            try {
                from = criteria.from() == null ? null : LocalDate.parse(criteria.from());
                to = criteria.to() == null ? null : LocalDate.parse(criteria.to());
            } catch (RuntimeException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history dates.");
            }
            if (from != null && to != null && from.isAfter(to))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "History dates are reversed.");
            if (from != null) {
                sql.append(" AND o.created_at>=?");
                args.add(from.atStartOfDay());
            }
            if (to != null) {
                sql.append(" AND o.created_at<?");
                try {
                    args.add(to.plusDays(1).atStartOfDay());
                } catch (RuntimeException invalid) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Invalid history dates.");
                }
            }
            if (cursor != null) {
                sql.append(
                        after != null
                                ? " AND (o.created_at,o.id)>(SELECT created_at,id FROM orders WHERE"
                                        + " order_number=?)"
                                : " AND (o.created_at,o.id)<(SELECT created_at,id FROM orders WHERE"
                                        + " order_number=?)");
                args.add(cursor);
            }
            sql.append(
                    after != null
                            ? " ORDER BY o.created_at ASC,o.id ASC LIMIT ?"
                            : " ORDER BY o.created_at DESC,o.id DESC LIMIT ?");
            args.add(criteria.limit() + 1);
            var rows = jdbc.query(sql.toString(), (rs, row) -> rs.getString(1), args.toArray());
            var visible = new ArrayList<>(rows.stream().limit(criteria.limit()).toList());
            boolean extra = rows.size() > criteria.limit();
            if (after != null) Collections.reverse(visible);
            return new Page(
                    List.copyOf(visible),
                    after != null ? extra : before != null,
                    after != null || extra);
        } finally {
            MethodTiming.finish(started, CustomerHistoryQuery.class, "read(String,UUID,Criteria)");
        }
    }
}
