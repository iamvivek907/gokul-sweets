package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

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

/** One immutable final invoice after physical packing; payment attempts remain untouched. */
@Service
@RequiredArgsConstructor
public class OccasionPackingFinalizer {

    private final JdbcTemplate jdbc;

    private final OccasionEnquiryService enquiries;

    private final OccasionOrderFinalizer orders;

    private final CustomerNotificationInbox notifications;

    private final Clock clock;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Immutable packed data contract. */
    public record Packed(
            @Positive long productId,
            @NotNull @DecimalMin("0.001") @Digits(integer = 8, fraction = 3) BigDecimal quantity) {}

    /** Immutable input data contract. */
    public record Input(
            @NotEmpty @Size(max = 30) List<@Valid Packed> lines,
            @Min(0) int revision,
            boolean requestedPiecesPacked) {}

    /**
     * Finalizes packing.
     *
     * @param env the env
     * @param branchId the branch id
     * @param id the id
     * @param actor the actor
     * @param input the input
     * @return the finalize packing result
     */
    @Transactional
    public OccasionEnquiryService.Summary finalizePacking(
            ConsentEnvironment env, long branchId, UUID id, String actor, Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionPackingFinalizer.class,
                        "finalizePacking(ConsentEnvironment,long,UUID,String,Input)");
        try {
            jdbc.query(
                    "SELECT id FROM occasion_enquiries WHERE id=? AND environment=? AND branch_id=?"
                            + " FOR UPDATE",
                    rs -> {
                        return null;
                    },
                    id,
                    env.name(),
                    branchId);
            var request = enquiries.staffGet(env, branchId, id);
            if (!request.estimated()
                    || !"PAID".equals(request.status())
                    || request.packingFinalizedAt() != null)
                conflict(
                        "Finalize only an estimated booking with a verified advance and no final"
                                + " invoice.");
            if (!request.serviceDate().equals(LocalDate.now(clock.withZone(IST))))
                conflict(
                        "Record final measured quantities after packing on the requested pickup"
                                + " date in India.");
            if (request.packingRevision() != input.revision())
                conflict("Another staff member changed this booking. Refresh first.");
            if (!input.requestedPiecesPacked())
                conflict("Confirm every requested piece and agreed box has been packed.");
            Boolean payment =
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM occasion_payment_attempts WHERE"
                                    + " enquiry_id=? AND stage='BALANCE')",
                            Boolean.class,
                            id);
            if (Boolean.TRUE.equals(payment))
                conflict(
                        "A balance checkout already exists; finance must review it before any price"
                                + " change.");
            Integer committed =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM occasion_production_allocations WHERE"
                                    + " enquiry_id=? AND state='COMMITTED'",
                            Integer.class,
                            id);
            if (committed == null || committed != request.items().size())
                conflict(
                        "Every quoted item must have a committed dedicated production allocation.");
            String json =
                    jdbc.queryForObject(
                            "SELECT quote_calculation::text FROM occasion_enquiries WHERE id=?",
                            String.class,
                            id);
            var mapper = new tools.jackson.databind.ObjectMapper();
            var quote = mapper.readValue(json, OccasionQuoteCalculator.Calculation.class);
            if (input.lines().size() != quote.lines().size()
                    || input.lines().stream().map(Packed::productId).distinct().count()
                            != quote.lines().size()) invalid("Record each quoted product once.");
            Instant cutoff =
                    jdbc.query(
                            "SELECT slot_date,end_time FROM pickup_slots WHERE id=?",
                            rs ->
                                    rs.next()
                                            ? LocalDateTime.of(
                                                            rs.getDate(1).toLocalDate(),
                                                            rs.getTime(2).toLocalTime())
                                                    .atZone(IST)
                                                    .toInstant()
                                            : null,
                            request.pickupSlotId());
            if (cutoff == null || !cutoff.isAfter(clock.instant().plusSeconds(60)))
                conflict(
                        "The pickup window has ended or is too close. Contact operations; do not"
                                + " take a new balance payment.");
            List<OccasionQuoteCalculator.Line> lines = new ArrayList<>();
            BigDecimal base = BigDecimal.ZERO, tax = BigDecimal.ZERO;
            for (var old : quote.lines()) {
                var packed =
                        input.lines().stream()
                                .filter(x -> x.productId() == old.productId())
                                .findFirst()
                                .orElse(null);
                if (packed == null) invalid("A quoted product is missing.");
                // API quantities use grams for weight-priced food; UI labels kg and converts once.
                BigDecimal actual = packed.quantity();
                if (actual.stripTrailingZeros().scale() > 0
                        || actual.compareTo(new BigDecimal("100000000")) > 0
                        || "GRAM".equals(old.unit()) && actual.compareTo(new BigDecimal("250")) < 0
                        || "PIECE".equals(old.unit())
                                && actual.compareTo(old.requestedQuantity()) != 0)
                    invalid("Enter whole grams (at least 250 g) or retain all requested pieces.");
                if ("GRAM".equals(old.requestedUnit())
                        && actual.compareTo(old.requestedQuantity()) < 0)
                    invalid(
                            "Packed weight must supply at least the customer's requested kg and"
                                    + " weight packs.");
                if (old.requestedSupplementalGrams() != null
                        && old.requestedSupplementalGrams().signum() > 0
                        && actual.compareTo(old.requestedSupplementalGrams()) <= 0)
                    invalid(
                            "Final weight must include the additional kg and all mixed-box"
                                    + " pieces.");
                BigDecimal originalBase =
                        OccasionQuoteCalculator.money(
                                old.unitPrice()
                                        .multiply(
                                                "GRAM".equals(old.unit())
                                                        ? actual.movePointLeft(3)
                                                        : actual));
                BigDecimal foodBase =
                        OccasionQuoteCalculator.discounted(
                                originalBase, old.rebatePercent(), quote.bulkRebatePercent());
                BigDecimal foodTax =
                        OccasionQuoteCalculator.money(
                                foodBase.multiply(old.cgstRate().add(old.sgstRate()))
                                        .movePointLeft(2));
                base = base.add(foodBase);
                tax = tax.add(foodTax);
                lines.add(
                        new OccasionQuoteCalculator.Line(
                                old.productId(),
                                old.name(),
                                old.unit(),
                                old.requestedQuantity(),
                                old.requestedUnit(),
                                old.unitPrice(),
                                old.pieceGrams(),
                                actual,
                                old.cgstRate(),
                                old.sgstRate(),
                                foodBase,
                                foodTax,
                                BigDecimal.ZERO,
                                foodBase.add(foodTax),
                                old.rebatePercent(),
                                originalBase,
                                originalBase.subtract(foodBase),
                                old.requestedSupplementalGrams()));
            }
            BigDecimal food = base.add(tax), allocated = BigDecimal.ZERO;
            for (int n = 0; n < lines.size(); n++) {
                var line = lines.get(n);
                BigDecimal part =
                        n == lines.size() - 1
                                ? quote.packagingTotal().subtract(allocated)
                                : quote.packagingTotal()
                                        .multiply(line.grossAmount())
                                        .divide(food, 2, RoundingMode.HALF_UP)
                                        .min(quote.packagingTotal().subtract(allocated));
                allocated = allocated.add(part);
                BigDecimal gross = line.grossAmount().add(part);
                var updated =
                        new OccasionQuoteCalculator.Line(
                                line.productId(),
                                line.name(),
                                line.unit(),
                                line.requestedQuantity(),
                                line.requestedUnit(),
                                line.unitPrice(),
                                line.pieceGrams(),
                                line.productionQuantity(),
                                line.cgstRate(),
                                line.sgstRate(),
                                line.foodBase(),
                                line.foodTax(),
                                part,
                                gross,
                                line.rebatePercent(),
                                line.originalFoodBase(),
                                line.rebateAmount(),
                                line.requestedSupplementalGrams());
                lines.set(n, updated);
                BigDecimal subtotal =
                        gross.divide(
                                BigDecimal.ONE.add(
                                        line.cgstRate().add(line.sgstRate()).movePointLeft(2)),
                                2,
                                RoundingMode.HALF_UP);
                jdbc.update(
                        """
UPDATE occasion_quote_lines SET quantity=?,weight_grams=?,gross_amount=?,subtotal=?,tax_amount=?
WHERE enquiry_id=? AND product_id=?
""",
                        "GRAM".equals(line.unit()) ? 1 : line.productionQuantity().intValueExact(),
                        "GRAM".equals(line.unit())
                                ? line.productionQuantity().intValueExact()
                                : null,
                        gross,
                        subtotal,
                        gross.subtract(subtotal),
                        id,
                        line.productId());
                jdbc.update(
                        "UPDATE occasion_enquiry_items SET approved_quantity=? WHERE enquiry_id=?"
                                + " AND product_id=?",
                        line.productionQuantity(),
                        id,
                        line.productId());
                // All requested pieces are packed. Record physical readiness separately from an
                // estimated plan.
                jdbc.update(
                        """
UPDATE occasion_production_allocations SET quantity=?,ready_quantity=?,readiness_revision=readiness_revision+1,updated_at=CURRENT_TIMESTAMP
WHERE enquiry_id=? AND product_id=? AND state='COMMITTED'
""",
                        line.productionQuantity(),
                        line.productionQuantity(),
                        id,
                        line.productId());
            }
            BigDecimal total = food.add(quote.packagingTotal()),
                    remaining = total.subtract(request.paidAmount());
            if (total.compareTo(new BigDecimal("9999999999.99")) > 0)
                invalid("Final invoice exceeds the supported amount.");
            var finalCalculation =
                    new OccasionQuoteCalculator.Calculation(
                            List.copyOf(lines),
                            base,
                            tax,
                            quote.packagingTotal(),
                            total,
                            request.depositAmount(),
                            remaining.max(BigDecimal.ZERO),
                            quote.expiresAt(),
                            cutoff,
                            quote.expectedReadyAt(),
                            quote.extras(),
                            true,
                            quote.bulkRebatePercent(),
                            lines.stream()
                                    .map(OccasionQuoteCalculator.Line::rebateAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add),
                            quote.packingCharges());
            int revision = request.packingRevision() + 1;
            jdbc.update(
                    """
INSERT INTO occasion_packing_finalizations(enquiry_id,revision,original_estimate,final_amount,calculation,actor)
VALUES(?,?,?,?,?::jsonb,?)
""",
                    id,
                    revision,
                    request.originalEstimate(),
                    total,
                    mapper.writeValueAsString(finalCalculation),
                    actor);
            jdbc.update(
                    """
UPDATE occasion_enquiries SET quoted_amount=?,packing_finalized_at=?,packing_revision=?,balance_due_at=?,
  credit_review_amount=?,quote_calculation=?::jsonb,updated_at=CURRENT_TIMESTAMP WHERE id=?
""",
                    total,
                    Timestamp.from(clock.instant()),
                    revision,
                    Timestamp.from(cutoff),
                    remaining.signum() < 0 ? remaining.negate() : null,
                    mapper.writeValueAsString(finalCalculation),
                    id);
            if (remaining.signum() <= 0) {
                orders.create(
                        id,
                        env,
                        jdbc.queryForObject(
                                "SELECT subject_id FROM occasion_enquiries WHERE id=?",
                                UUID.class,
                                id),
                        branchId,
                        request.pickupSlotId(),
                        total);
                jdbc.update(
                        "UPDATE occasion_enquiries SET status='CONFIRMED',confirmed_at=? WHERE"
                                + " id=?",
                        Timestamp.from(clock.instant()),
                        id);
            }
            jdbc.update(
                    "INSERT INTO"
                        + " occasion_enquiry_events(enquiry_id,actor,from_status,to_status,detail)"
                        + " VALUES(?,?,'PAID',?,'Packing finalized; measured weights and final"
                        + " invoice recorded')",
                    id,
                    actor,
                    remaining.signum() <= 0 ? "CONFIRMED" : "PAID");
            notifications.occasionDecisionChanged(id);
            return enquiries.staffGet(env, branchId, id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionPackingFinalizer.class,
                    "finalizePacking(ConsentEnvironment,long,UUID,String,Input)");
        }
    }

    /**
     * Invalids the operation.
     *
     * @param message the message
     */
    private static void invalid(String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionPackingFinalizer.class, "invalid(String)");
        try {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionPackingFinalizer.class, "invalid(String)");
        }
    }

    /**
     * Conflicts the operation.
     *
     * @param message the message
     */
    private static void conflict(String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionPackingFinalizer.class, "conflict(String)");
        try {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionPackingFinalizer.class, "conflict(String)");
        }
    }
}
