package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.identity.VerifiedCustomerPhoneLookup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** A request and a reviewed price are never a capacity reservation or a paid order. */
@Service
@RequiredArgsConstructor
public class OccasionEnquiryService {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private final JdbcTemplate jdbc;
    private final EnhancementProperties features;
    private final VerifiedCustomerPhoneLookup customers;
    private final Clock clock;

    public record Item(@Positive long productId, @DecimalMin("0.001") @Digits(integer = 9, fraction = 3)
                       BigDecimal quantity, @NotNull Unit unit, String productName) {}
    public enum Unit { PIECE, GRAM }
    public enum Fulfilment { PICKUP, DELIVERY_REQUEST }
    public record Request(@Positive long branchId, @NotBlank @Size(max = 80) String occasionType,
                          @NotNull LocalDate serviceDate, @Min(1) @Max(10000) int guestCount,
                          @NotNull Fulfilment fulfilment, @Size(max = 500) String deliveryAddress,
                          @Size(max = 1000) String notes,
                          @NotEmpty @Size(max = 30) List<@Valid Item> items) {}
    public record Quote(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal deposit,
                        @NotNull Instant expiresAt, @NotBlank @Size(max = 500) String terms) {}
    public record Summary(UUID id, long branchId, String occasionType, LocalDate serviceDate, int guestCount,
                          Fulfilment fulfilment, String status, BigDecimal quotedAmount,
                          BigDecimal depositAmount, BigDecimal paidAmount, String quoteTerms, Instant quoteExpiresAt,
                          Instant createdAt, String nextStep, String customerPhone, String deliveryAddress,
                          String notes, List<Item> items) {}

    @Transactional
    public Summary submit(ConsentEnvironment environment, UUID subject, Request input) {
        if (!features.isOccasionEnquiries()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (input.serviceDate() == null || !input.serviceDate().isAfter(LocalDate.now(clock.withZone(IST)))
                || input.serviceDate().isAfter(LocalDate.now(clock.withZone(IST)).plusDays(365)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a future date within one year.");
        if (input.fulfilment() == Fulfilment.PICKUP && input.deliveryAddress() != null && !input.deliveryAddress().isBlank()
                || input.fulfilment() == Fulfilment.DELIVERY_REQUEST && (input.deliveryAddress() == null || input.deliveryAddress().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A delivery request needs an address; pickup does not.");
        if (customers.verifiedPhone(environment, subject).isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (input.items() == null || input.items().isEmpty() || input.items().size() > 30
                || input.items().stream().anyMatch(i -> i == null || i.quantity() == null || i.quantity().signum() <= 0
                || i.quantity().compareTo(new BigDecimal("100000")) > 0 || i.unit() == null
                || i.unit() == Unit.PIECE && i.quantity().stripTrailingZeros().scale() > 0)
                || input.items().stream().map(Item::productId).distinct().count() != input.items().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose distinct products and valid quantities.");
        // Serialize submissions for this verified subject so concurrent tabs cannot evade the limit.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))", subject.toString());
        String requestHash = requestHash(input);
        List<UUID> duplicate = jdbc.query("""
                SELECT id FROM occasion_enquiries WHERE environment = ? AND subject_id = ?
                  AND request_hash = ? AND created_at >= ? ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> (UUID) rs.getObject(1), environment.name(), subject, requestHash,
                Timestamp.from(clock.instant().minus(Duration.ofMinutes(15))));
        if (!duplicate.isEmpty()) return get(environment, subject, duplicate.getFirst());
        Integer recent = jdbc.queryForObject("""
                SELECT count(*) FROM occasion_enquiries WHERE environment = ? AND subject_id = ?
                  AND created_at >= ?
                """, Integer.class, environment.name(), subject, Timestamp.from(clock.instant().minus(Duration.ofDays(1))));
        if (recent != null && recent >= 3)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have sent three enquiries in the last 24 hours. Please contact the branch for changes.");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM branches WHERE id = ? AND active)",
                Boolean.class, input.branchId()))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch is unavailable.");
        for (Item item : input.items()) {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM branch_products bp JOIN products p ON p.id = bp.product_id
                    WHERE bp.branch_id = ? AND bp.product_id = ? AND bp.available AND p.active
                    AND ((p.sale_mode = 'WEIGHT' AND ? = 'GRAM' AND ? >= p.minimum_weight_grams
                          AND MOD(?, p.weight_step_grams) = 0)
                      OR (p.sale_mode = 'UNIT' AND ? = 'PIECE')))
                    """, Boolean.class, input.branchId(), item.productId(), item.unit().name(),
                    item.quantity(), item.quantity(), item.unit().name())))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A selected product or quantity is unavailable at this branch.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO occasion_enquiries (id, environment, subject_id, branch_id, occasion_type,
                    service_date, guest_count, fulfilment, delivery_address, notes, request_hash, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'REQUESTED')
                """, id, environment.name(), subject, input.branchId(), input.occasionType().trim(),
                Date.valueOf(input.serviceDate()), input.guestCount(), input.fulfilment().name(),
                input.fulfilment() == Fulfilment.PICKUP ? null : input.deliveryAddress().trim(), input.notes(), requestHash);
        for (Item item : input.items()) jdbc.update("""
                INSERT INTO occasion_enquiry_items (enquiry_id, product_id, requested_quantity, unit)
                VALUES (?, ?, ?, ?)
                """, id, item.productId(), item.quantity(), item.unit().name());
        event(id, "customer", null, "REQUESTED", null);
        return get(environment, subject, id);
    }

    @Transactional(readOnly = true)
    public List<Summary> customerList(ConsentEnvironment environment, UUID subject) {
        return jdbc.query("""
                SELECT * FROM occasion_enquiries WHERE environment = ? AND subject_id = ? ORDER BY created_at DESC LIMIT 100
                """, (rs, row) -> map(rs), environment.name(), subject);
    }

    @Transactional(readOnly = true)
    public Summary get(ConsentEnvironment environment, UUID subject, UUID id) {
        return jdbc.query("SELECT * FROM occasion_enquiries WHERE id = ? AND environment = ? AND subject_id = ?",
                rs -> rs.next() ? map(rs) : null, id, environment.name(), subject) instanceof Summary result ? result
                : throwNotFound();
    }

    @Transactional(readOnly = true)
    public List<Summary> staffList(ConsentEnvironment environment, long branchId) {
        return jdbc.query("""
                SELECT * FROM occasion_enquiries WHERE environment = ? AND branch_id = ? ORDER BY created_at DESC LIMIT 100
                """, (rs, row) -> map(rs), environment.name(), branchId);
    }

    @Transactional
    public Summary quote(ConsentEnvironment environment, long branchId, UUID id, String staff, Quote quote) {
        if (quote.deposit().compareTo(quote.amount()) > 0 || !quote.expiresAt().isAfter(clock.instant())
                || quote.expiresAt().isAfter(clock.instant().plus(Duration.ofDays(14))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quote amount or expiry is invalid.");
        String before = lockedStatus(environment, branchId, id);
        if (!"REQUESTED".equals(before) && !"QUOTED".equals(before))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an open request can be quoted.");
        if ("QUOTED".equals(before) && expired(environment, branchId, id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This quote expired; ask for a new enquiry.");
        jdbc.update("""
                UPDATE occasion_enquiries SET status = 'QUOTED', quoted_amount = ?, deposit_amount = ?,
                    quote_terms = ?, quote_expires_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, quote.amount(), quote.deposit(), quote.terms().trim(), Timestamp.from(quote.expiresAt()), id);
        event(id, staff, before, "QUOTED", quote.terms().trim());
        return staffGet(environment, branchId, id);
    }

    @Transactional
    public Summary decline(ConsentEnvironment environment, long branchId, UUID id, String staff, String reason) {
        String before = lockedStatus(environment, branchId, id);
        if (!"REQUESTED".equals(before) && !"QUOTED".equals(before))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is no longer open.");
        jdbc.update("UPDATE occasion_enquiries SET status = 'DECLINED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        event(id, staff, before, "DECLINED", reason == null ? null : reason.substring(0, Math.min(500, reason.length())));
        return staffGet(environment, branchId, id);
    }

    private String lockedStatus(ConsentEnvironment environment, long branchId, UUID id) {
        return jdbc.query("SELECT status FROM occasion_enquiries WHERE id = ? AND environment = ? AND branch_id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getString(1) : null, id, environment.name(), branchId) instanceof String result
                ? result : throwNotFound();
    }

    private boolean expired(ConsentEnvironment environment, long branchId, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT quote_expires_at <= ? FROM occasion_enquiries
                WHERE id = ? AND environment = ? AND branch_id = ?
                """, Boolean.class, Timestamp.from(clock.instant()), id, environment.name(), branchId));
    }

    private Summary staffGet(ConsentEnvironment environment, long branchId, UUID id) {
        return jdbc.query("SELECT * FROM occasion_enquiries WHERE id = ? AND environment = ? AND branch_id = ?",
                rs -> rs.next() ? map(rs) : null, id, environment.name(), branchId);
    }

    private void event(UUID id, String actor, String before, String after, String detail) {
        jdbc.update("INSERT INTO occasion_enquiry_events (enquiry_id, actor, from_status, to_status, detail) VALUES (?, ?, ?, ?, ?)",
                id, actor, before, after, detail);
    }

    private static String requestHash(Request input) {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, Long.toString(input.branchId()));
        appendField(canonical, input.occasionType().trim());
        appendField(canonical, input.serviceDate().toString());
        appendField(canonical, Integer.toString(input.guestCount()));
        appendField(canonical, input.fulfilment().name());
        appendField(canonical, input.deliveryAddress() == null ? "" : input.deliveryAddress().trim());
        appendField(canonical, input.notes() == null ? "" : input.notes().trim());
        input.items().stream().sorted(Comparator.comparingLong(Item::productId))
                .forEach(item -> {
                    appendField(canonical, Long.toString(item.productId()));
                    appendField(canonical, item.quantity().stripTrailingZeros().toPlainString());
                    appendField(canonical, item.unit().name());
                });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable.", unavailable);
        }
    }

    private static void appendField(StringBuilder canonical, String value) {
        canonical.append(value.length()).append(':').append(value);
    }

    private Summary map(java.sql.ResultSet rs) throws java.sql.SQLException {
        String status = rs.getString("status");
        Instant expiry = rs.getTimestamp("quote_expires_at") == null ? null : rs.getTimestamp("quote_expires_at").toInstant();
        if ("QUOTED".equals(status) && expiry != null && !expiry.isAfter(clock.instant())) status = "EXPIRED";
        String next = switch (status) {
            case "REQUESTED" -> "Your request is with the branch. No booking or payment is due yet.";
            case "QUOTED" -> "A manager has prepared a quote. Contact the branch to review it; no booking is confirmed.";
            case "EXPIRED" -> "This quote has expired. Ask the branch for a new quote.";
            case "DECLINED" -> "The branch cannot take this request. Please choose another date or branch.";
            default -> "The branch will confirm the next step. This is not a confirmed booking.";
        };
        return new Summary((UUID) rs.getObject("id"), rs.getLong("branch_id"), rs.getString("occasion_type"),
                rs.getDate("service_date").toLocalDate(), rs.getInt("guest_count"),
                Fulfilment.valueOf(rs.getString("fulfilment")), status, rs.getBigDecimal("quoted_amount"),
                rs.getBigDecimal("deposit_amount"), rs.getBigDecimal("paid_amount"), rs.getString("quote_terms"), expiry,
                rs.getTimestamp("created_at").toInstant(), next,
                customers.verifiedPhone(ConsentEnvironment.valueOf(rs.getString("environment")),
                        (UUID) rs.getObject("subject_id")).orElse(""),
                rs.getString("delivery_address"), rs.getString("notes"),
                jdbc.query("""
                        SELECT i.product_id, i.requested_quantity, i.unit, p.name
                        FROM occasion_enquiry_items i JOIN products p ON p.id = i.product_id
                        WHERE i.enquiry_id = ? ORDER BY p.name
                        """,
                        (items, row) -> new Item(items.getLong(1), items.getBigDecimal(2),
                                Unit.valueOf(items.getString(3)), items.getString(4)),
                        (UUID) rs.getObject("id")));
    }

    private static <T> T throwNotFound() { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
