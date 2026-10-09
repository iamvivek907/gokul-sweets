package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
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
import java.time.*;
import java.util.*;

/** Staff supplies rates and measured sizes, never pre-calculated tax-inclusive totals. */
@Service
@RequiredArgsConstructor
public class OccasionQuoteCalculator {

    private final JdbcTemplate jdbc;

    private final OccasionEnquiryService enquiries;

    private final Clock clock;

    private final com.gokulsweets.restaurant.config.EnhancementProperties features;

    private final com.gokulsweets.restaurant.tax.TaxCollectionSettings taxCollection;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * Immutable rate data contract.
     *
     * @param productId the product id
     * @param unitPrice the unit price
     * @param pieceGrams the piece grams
     * @param estimatedKg the estimated kg
     * @param rebatePercent the rebate percent
     */
    public record Rate(
            @Positive long productId,
            @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice,
            @DecimalMin("0.001") @Digits(integer = 6, fraction = 3) BigDecimal pieceGrams,
            @DecimalMin("0.001") @Digits(integer = 8, fraction = 3) BigDecimal estimatedKg,
            @DecimalMin("0") @DecimalMax("99") BigDecimal rebatePercent) {

        /**
         * Creates a rate instance.
         *
         * @param productId the product id
         * @param unitPrice the unit price
         * @param pieceGrams the piece grams
         * @param estimatedKg the estimated kg
         */
        public Rate(
                long productId,
                BigDecimal unitPrice,
                BigDecimal pieceGrams,
                BigDecimal estimatedKg) {
            this(productId, unitPrice, pieceGrams, estimatedKg, null);
        }
    }

    /**
     * Immutable input data contract.
     *
     * @param rates the rates
     * @param depositPercent the deposit percent
     * @param packagingPerBox the packaging per box
     * @param packagingReviewed the packaging reviewed
     * @param readyTime the ready time
     * @param terms the terms
     * @param estimated the estimated
     * @param extras the extras
     * @param expectedTotal the expected total
     * @param bulkRebatePercent the bulk rebate percent
     * @param packingRates the packing rates
     */
    public record Input(
            @NotEmpty @Size(max = 30) List<@Valid Rate> rates,
            @NotNull @DecimalMin("1") @DecimalMax("100") BigDecimal depositPercent,
            @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal packagingPerBox,
            boolean packagingReviewed,
            LocalTime readyTime,
            @NotBlank @Size(max = 500) String terms,
            boolean estimated,
            @Size(max = 10) List<@Valid Extra> extras,
            BigDecimal expectedTotal,
            @DecimalMin("0") @DecimalMax("99") BigDecimal bulkRebatePercent,
            @Size(max = 30) List<@Valid PackingRate> packingRates) {

        /**
         * Creates a input instance.
         *
         * @param rates the rates
         * @param depositPercent the deposit percent
         * @param packagingPerBox the packaging per box
         * @param packagingReviewed the packaging reviewed
         * @param readyTime the ready time
         * @param terms the terms
         * @param estimated the estimated
         * @param extras the extras
         * @param expectedTotal the expected total
         */
        public Input(
                List<Rate> rates,
                BigDecimal depositPercent,
                BigDecimal packagingPerBox,
                boolean packagingReviewed,
                LocalTime readyTime,
                String terms,
                boolean estimated,
                List<Extra> extras,
                BigDecimal expectedTotal) {
            this(
                    rates,
                    depositPercent,
                    packagingPerBox,
                    packagingReviewed,
                    readyTime,
                    terms,
                    estimated,
                    extras,
                    expectedTotal,
                    null,
                    null);
        }
    }

    /**
     * Immutable packing rate data contract.
     *
     * @param groupNumber the group number
     * @param pricePerBox the price per box
     */
    public record PackingRate(
            @Min(1) int groupNumber,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal pricePerBox) {}

    /**
     * Immutable packing charge data contract.
     *
     * @param groupNumber the group number
     * @param name the name
     * @param quantity the quantity
     * @param pricePerBox the price per box
     * @param total the total
     */
    public record PackingCharge(
            int groupNumber, String name, int quantity, BigDecimal pricePerBox, BigDecimal total) {}

    /**
     * Immutable extra data contract.
     *
     * @param name the name
     * @param quantity the quantity
     * @param priceIncludingTax the price including tax
     */
    public record Extra(
            @NotBlank @Size(max = 100) String name,
            @Min(1) @Max(100000) int quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2)
                    BigDecimal priceIncludingTax) {}

    /**
     * Immutable line data contract.
     *
     * @param productId the product id
     * @param name the name
     * @param unit the unit
     * @param requestedQuantity the requested quantity
     * @param requestedUnit the requested unit
     * @param unitPrice the unit price
     * @param pieceGrams the piece grams
     * @param productionQuantity the production quantity
     * @param cgstRate the cgst rate
     * @param sgstRate the sgst rate
     * @param foodBase the food base
     * @param foodTax the food tax
     * @param packagingAmount the packaging amount
     * @param grossAmount the gross amount
     * @param rebatePercent the rebate percent
     * @param originalFoodBase the original food base
     * @param rebateAmount the rebate amount
     * @param requestedSupplementalGrams the requested supplemental grams
     */
    public record Line(
            long productId,
            String name,
            String unit,
            BigDecimal requestedQuantity,
            String requestedUnit,
            BigDecimal unitPrice,
            BigDecimal pieceGrams,
            BigDecimal productionQuantity,
            BigDecimal cgstRate,
            BigDecimal sgstRate,
            BigDecimal foodBase,
            BigDecimal foodTax,
            BigDecimal packagingAmount,
            BigDecimal grossAmount,
            BigDecimal rebatePercent,
            BigDecimal originalFoodBase,
            BigDecimal rebateAmount,
            BigDecimal requestedSupplementalGrams) {

        /**
         * Creates a line instance.
         *
         * @param productId the product id
         * @param name the name
         * @param unit the unit
         * @param requestedQuantity the requested quantity
         * @param requestedUnit the requested unit
         * @param unitPrice the unit price
         * @param pieceGrams the piece grams
         * @param productionQuantity the production quantity
         * @param cgstRate the cgst rate
         * @param sgstRate the sgst rate
         * @param foodBase the food base
         * @param foodTax the food tax
         * @param packagingAmount the packaging amount
         * @param grossAmount the gross amount
         */
        public Line(
                long productId,
                String name,
                String unit,
                BigDecimal requestedQuantity,
                String requestedUnit,
                BigDecimal unitPrice,
                BigDecimal pieceGrams,
                BigDecimal productionQuantity,
                BigDecimal cgstRate,
                BigDecimal sgstRate,
                BigDecimal foodBase,
                BigDecimal foodTax,
                BigDecimal packagingAmount,
                BigDecimal grossAmount) {
            this(
                    productId,
                    name,
                    unit,
                    requestedQuantity,
                    requestedUnit,
                    unitPrice,
                    pieceGrams,
                    productionQuantity,
                    cgstRate,
                    sgstRate,
                    foodBase,
                    foodTax,
                    packagingAmount,
                    grossAmount,
                    BigDecimal.ZERO,
                    foodBase,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }
    }

    /**
     * Immutable calculation data contract.
     *
     * @param lines the lines
     * @param foodBase the food base
     * @param foodTax the food tax
     * @param packagingTotal the packaging total
     * @param total the total
     * @param deposit the deposit
     * @param balance the balance
     * @param expiresAt the expires at
     * @param balanceDueAt the balance due at
     * @param expectedReadyAt the expected ready at
     * @param extras the extras
     * @param estimated the estimated
     * @param bulkRebatePercent the bulk rebate percent
     * @param rebateTotal the rebate total
     * @param packingCharges the packing charges
     */
    public record Calculation(
            List<Line> lines,
            BigDecimal foodBase,
            BigDecimal foodTax,
            BigDecimal packagingTotal,
            BigDecimal total,
            BigDecimal deposit,
            BigDecimal balance,
            Instant expiresAt,
            Instant balanceDueAt,
            LocalDateTime expectedReadyAt,
            List<Extra> extras,
            boolean estimated,
            BigDecimal bulkRebatePercent,
            BigDecimal rebateTotal,
            List<PackingCharge> packingCharges) {

        /**
         * Creates a calculation instance.
         *
         * @param lines the lines
         * @param foodBase the food base
         * @param foodTax the food tax
         * @param packagingTotal the packaging total
         * @param total the total
         * @param deposit the deposit
         * @param balance the balance
         * @param expiresAt the expires at
         * @param balanceDueAt the balance due at
         * @param expectedReadyAt the expected ready at
         * @param extras the extras
         * @param estimated the estimated
         */
        public Calculation(
                List<Line> lines,
                BigDecimal foodBase,
                BigDecimal foodTax,
                BigDecimal packagingTotal,
                BigDecimal total,
                BigDecimal deposit,
                BigDecimal balance,
                Instant expiresAt,
                Instant balanceDueAt,
                LocalDateTime expectedReadyAt,
                List<Extra> extras,
                boolean estimated) {
            this(
                    lines,
                    foodBase,
                    foodTax,
                    packagingTotal,
                    total,
                    deposit,
                    balance,
                    expiresAt,
                    balanceDueAt,
                    expectedReadyAt,
                    extras,
                    estimated,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of());
        }
    }

    /**
     * Immutable source data contract.
     *
     * @param id the id
     * @param name the name
     * @param weight the weight
     * @param quantity the quantity
     * @param requestedUnit the requested unit
     * @param unitPrice the unit price
     * @param pieceGrams the piece grams
     * @param cgst the cgst
     * @param sgst the sgst
     * @param supplementalGrams the supplemental grams
     */
    private record Source(
            long id,
            String name,
            boolean weight,
            BigDecimal quantity,
            String requestedUnit,
            BigDecimal unitPrice,
            BigDecimal pieceGrams,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal supplementalGrams) {}

    /**
     * Previews occasion quote calculator data and returns the {@code Calculation} result.
     *
     * <p>Reads {@code branch_products}, {@code occasion_enquiries}, {@code occasion_enquiry_items},
     * {@code products}, {@code tax_categories}.
     *
     * @param env the env supplied to this method
     * @param branchId the branch id supplied to this method
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     * @return the {@code Calculation} result
     */
    @Transactional(readOnly = true)
    public Calculation preview(ConsentEnvironment env, long branchId, UUID id, Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionQuoteCalculator.class,
                        "preview(ConsentEnvironment,long,UUID,Input)");
        try {
            var request = enquiries.staffGet(env, branchId, id);
            if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (input.estimated() && !features.isOccasionBulkProduction())
                invalid(
                        "Enable dedicated bulk production before issuing a measured-at-packing"
                                + " estimate.");
            if (!Set.of("REQUESTED", "QUOTED").contains(request.status()))
                invalid("Only an open request can be quoted.");
            var sources =
                    jdbc.query(
                            """
SELECT p.id,p.name,p.sale_mode,i.requested_quantity,i.unit,
COALESCE(bp.price_override,p.base_price) AS unit_price,bp.occasion_piece_grams,
tc.cgst_rate,tc.sgst_rate,i.supplemental_grams
FROM occasion_enquiry_items i JOIN products p ON p.id=i.product_id
JOIN tax_categories tc ON tc.id=p.tax_category_id AND tc.active
JOIN occasion_enquiries e ON e.id=i.enquiry_id
LEFT JOIN branch_products bp ON bp.product_id=p.id AND bp.branch_id=e.branch_id
WHERE i.enquiry_id=? ORDER BY p.id
""",
                            (rs, n) ->
                                    new Source(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            "WEIGHT".equals(rs.getString(3)),
                                            rs.getBigDecimal(4),
                                            rs.getString(5),
                                            rs.getBigDecimal(6),
                                            rs.getBigDecimal(7),
                                            rs.getBigDecimal(8),
                                            rs.getBigDecimal(9),
                                            rs.getBigDecimal(10)),
                            id);
            if (sources.size() != request.items().size()
                    || input.rates().size() != sources.size()
                    || input.rates().stream().map(Rate::productId).distinct().count()
                            != sources.size())
                invalid("Price each requested product once and configure an active tax category.");
            BigDecimal packaging = BigDecimal.ZERO.setScale(2);
            if (request.gift() != null) {
                BigDecimal perBox =
                        input.packagingPerBox() != null
                                ? input.packagingPerBox()
                                : request.gift().box().price();
                if (perBox == null || perBox.signum() < 0)
                    invalid("Enter the reviewed packaging price per box.");
                packaging = money(perBox.multiply(BigDecimal.valueOf(request.gift().boxCount())));
            } else if (input.packagingPerBox() != null && input.packagingPerBox().signum() != 0)
                invalid("This request has no gift boxes.");
            var packingCharges = new ArrayList<PackingCharge>();
            var packingRates =
                    input.packingRates() == null ? List.<PackingRate>of() : input.packingRates();
            if (packingRates.stream().map(PackingRate::groupNumber).distinct().count()
                            != packingRates.size()
                    || packingRates.stream()
                            .anyMatch(
                                    rate ->
                                            request.packingGroups().stream()
                                                    .noneMatch(
                                                            group ->
                                                                    group.groupNumber()
                                                                            == rate.groupNumber())))
                invalid("Price each packing group once.");
            for (var group : request.packingGroups()) {
                var rate =
                        packingRates.stream()
                                .filter(x -> x.groupNumber() == group.groupNumber())
                                .findFirst()
                                .orElse(null);
                BigDecimal price = rate == null ? group.box().price() : rate.pricePerBox();
                if (price == null || price.signum() < 0 || price.scale() > 2)
                    invalid(
                            "Enter the reviewed per-box rate for packing group "
                                    + group.groupNumber()
                                    + ".");
                var amount = money(price.multiply(BigDecimal.valueOf(group.boxCount())));
                packaging = packaging.add(amount);
                packingCharges.add(
                        new PackingCharge(
                                group.groupNumber(),
                                group.box().name(),
                                group.boxCount(),
                                price,
                                amount));
            }
            BigDecimal bulkRebate = percent(input.bulkRebatePercent());
            List<Extra> extras = input.extras() == null ? List.of() : input.extras();
            for (Extra extra : extras)
                packaging =
                        packaging.add(
                                money(
                                        extra.priceIncludingTax()
                                                .multiply(BigDecimal.valueOf(extra.quantity()))));
            if (input.estimated()
                    && (request.fulfilment() != OccasionEnquiryService.Fulfilment.PICKUP
                            || input.depositPercent().compareTo(new BigDecimal("100")) >= 0))
                invalid(
                        "Measured-at-packing estimates need pickup and a part advance, so the final"
                                + " balance can be reviewed before collection.");
            boolean collectTax = taxCollection.enabled();
            List<Line> lines = new ArrayList<>();
            BigDecimal base = BigDecimal.ZERO, tax = BigDecimal.ZERO;
            for (var source : sources) {
                Rate rate =
                        input.rates().stream()
                                .filter(x -> x.productId() == source.id())
                                .findFirst()
                                .orElse(null);
                if (rate == null) {
                    invalid("A requested product is missing from the rates.");
                }
                BigDecimal price = rate.unitPrice() == null ? source.unitPrice() : rate.unitPrice();
                if (price == null || price.signum() <= 0 || price.scale() > 2)
                    invalid("Enter a positive rate with at most two decimal places.");
                BigDecimal piece =
                        rate.pieceGrams() == null ? source.pieceGrams() : rate.pieceGrams();
                BigDecimal production = source.quantity();
                if (source.weight()
                        && "PIECE".equals(source.requestedUnit())
                        && rate.estimatedKg() == null) {
                    if (piece == null || piece.signum() <= 0)
                        invalid(
                                "Measure grams per piece for "
                                        + source.name()
                                        + " before quoting.");
                    production = source.quantity().multiply(piece).add(source.supplementalGrams());
                }
                if (rate.estimatedKg() != null) {
                    if (!source.weight())
                        invalid("Estimated kg applies only to weight-priced food.");
                    production = rate.estimatedKg().movePointRight(3);
                }
                if (production.signum() <= 0
                        || production.stripTrailingZeros().scale() > 0
                        || production.compareTo(new BigDecimal("100000000")) > 0
                        || source.weight() && production.compareTo(new BigDecimal("250")) < 0)
                    invalid("Production must be a positive whole-gram or whole-piece quantity.");
                if ("GRAM".equals(source.requestedUnit())
                        && production.compareTo(source.quantity()) < 0)
                    invalid("Supply at least the customer's requested kg.");
                if (production.compareTo(source.supplementalGrams()) <= 0
                        && source.supplementalGrams().signum() > 0)
                    invalid("Estimated kg must include mixed-box pieces plus all additional kg.");
                BigDecimal originalBase =
                        money(
                                price.multiply(
                                        source.weight()
                                                ? production.movePointLeft(3)
                                                : production));
                BigDecimal rebate = percent(rate.rebatePercent());
                BigDecimal foodBase = discounted(originalBase, rebate, bulkRebate);
                BigDecimal cgst = collectTax ? source.cgst() : BigDecimal.ZERO;
                BigDecimal sgst = collectTax ? source.sgst() : BigDecimal.ZERO;
                BigDecimal foodTax = money(foodBase.multiply(cgst.add(sgst)).movePointLeft(2));
                base = base.add(foodBase);
                tax = tax.add(foodTax);
                lines.add(
                        new Line(
                                source.id(),
                                source.name(),
                                source.weight() ? "GRAM" : "PIECE",
                                source.quantity(),
                                source.requestedUnit(),
                                price,
                                piece,
                                production,
                                cgst,
                                sgst,
                                foodBase,
                                foodTax,
                                BigDecimal.ZERO.setScale(2),
                                foodBase.add(foodTax),
                                rebate,
                                originalBase,
                                originalBase.subtract(foodBase),
                                source.supplementalGrams()));
            }
            BigDecimal foodTotal = base.add(tax);
            if (foodTotal.signum() <= 0) invalid("Food total must be positive.");
            // The established booking snapshot carries packaging within item gross prices.
            // Allocate its inclusive cost once; final line absorbs the rounding remainder.
            BigDecimal allocated = BigDecimal.ZERO;
            for (int n = 0; n < lines.size(); n++) {
                Line line = lines.get(n);
                BigDecimal part =
                        n == lines.size() - 1
                                ? packaging.subtract(allocated)
                                : packaging
                                        .multiply(line.grossAmount())
                                        .divide(foodTotal, 2, RoundingMode.HALF_UP);
                // Bound rounding so tiny lines cannot over-allocate the remaining amount.
                part = part.min(packaging.subtract(allocated));
                allocated = allocated.add(part);
                lines.set(
                        n,
                        new Line(
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
                                line.grossAmount().add(part),
                                line.rebatePercent(),
                                line.originalFoodBase(),
                                line.rebateAmount(),
                                line.requestedSupplementalGrams()));
            }
            BigDecimal total = foodTotal.add(packaging);
            if (total.compareTo(new BigDecimal("9999999999.99")) > 0)
                invalid("Quote exceeds the supported amount.");
            if (input.depositPercent() == null
                    || input.depositPercent().compareTo(BigDecimal.ONE) < 0
                    || input.depositPercent().compareTo(new BigDecimal("100")) > 0)
                invalid("Choose an advance between 1 and 100 percent.");
            BigDecimal deposit = money(total.multiply(input.depositPercent()).movePointLeft(2));
            BigDecimal balance = total.subtract(deposit);
            if (input.estimated()
                    && !request.serviceDate().isAfter(LocalDate.now(clock.withZone(IST))))
                invalid("Choose a future date for the estimated booking.");
            Instant lastDeadline =
                    request.serviceDate().atStartOfDay(IST).toInstant().minusSeconds(1);
            Instant balanceDue = balance.signum() > 0 ? lastDeadline : null;
            Instant expiry = clock.instant().plus(Duration.ofHours(24));
            Instant latestExpiry = lastDeadline.minus(Duration.ofHours(1));
            if (expiry.isAfter(latestExpiry)) expiry = latestExpiry;
            if (!expiry.isAfter(clock.instant()))
                invalid(
                        "Not enough time remains before this date to issue a payable quote. Decline"
                            + " with an explanation and ask the customer to request a later date.");
            LocalDateTime ready = null;
            if (request.fulfilment() == OccasionEnquiryService.Fulfilment.PICKUP) {
                if (input.readyTime() == null)
                    invalid("Choose a ready time on the customer's requested date.");
                ready = request.serviceDate().atTime(input.readyTime());
            }
            if (input.estimated())
                balanceDue = request.serviceDate().atTime(23, 59, 59).atZone(IST).toInstant();
            return new Calculation(
                    List.copyOf(lines),
                    money(base),
                    money(tax),
                    packaging,
                    total,
                    deposit,
                    balance,
                    expiry,
                    balanceDue,
                    ready,
                    List.copyOf(extras),
                    input.estimated(),
                    bulkRebate,
                    lines.stream().map(Line::rebateAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                    List.copyOf(packingCharges));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionQuoteCalculator.class,
                    "preview(ConsentEnvironment,long,UUID,Input)");
        }
    }

    /**
     * Approves occasion quote calculator data and returns the {@code
     * OccasionEnquiryService.Summary} result.
     *
     * <p>Reads {@code occasion_enquiries}.
     *
     * <p>Writes {@code occasion_enquiries}, {@code occasion_enquiry_events}.
     *
     * @param env the env supplied to this method
     * @param branchId the branch id supplied to this method
     * @param id the id supplied to this method
     * @param staff the staff supplied to this method
     * @param input the input supplied to this method
     * @return the {@code OccasionEnquiryService.Summary} result
     * @throws ResponseStatusException when the method rejects the request with {@code Rates or
     *     quantities changed. Calculate and review the quote again before sending.}
     */
    @Transactional
    public OccasionEnquiryService.Summary approve(
            ConsentEnvironment env, long branchId, UUID id, String staff, Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionQuoteCalculator.class,
                        "approve(ConsentEnvironment,long,UUID,String,Input)");
        try {
            // Re-read authoritative rates/tax under the request lock. Preview alone never reserves
            // stock.
            jdbc.query(
                    "SELECT id FROM occasion_enquiries WHERE id=? AND environment=? AND branch_id=?"
                            + " FOR UPDATE",
                    rs -> {
                        return null;
                    },
                    id,
                    env.name(),
                    branchId);
            // Owner updates wait until both the calculation snapshot and persisted quote lines are
            // saved.
            taxCollection.lockForQuoteApproval();
            Calculation result = preview(env, branchId, id, input);
            if (input.expectedTotal() == null
                    || input.expectedTotal().compareTo(result.total()) != 0)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Rates or quantities changed. Calculate and review the quote again before"
                                + " sending.");
            // Existing fixed-quote validation requires a pre-event balance date; estimated final
            // balances
            // are deliberately blocked until packing and use the pickup-day deadline persisted
            // below.
            Instant legacyDue = result.balanceDueAt();
            if (input.estimated())
                legacyDue =
                        enquiries
                                .staffGet(env, branchId, id)
                                .serviceDate()
                                .atStartOfDay(IST)
                                .toInstant()
                                .minusSeconds(1);
            enquiries.quote(
                    env,
                    branchId,
                    id,
                    staff,
                    new OccasionEnquiryService.Quote(
                            result.total(),
                            result.deposit(),
                            result.expiresAt(),
                            legacyDue,
                            input.terms(),
                            result.lines().stream()
                                    .map(
                                            x ->
                                                    new OccasionEnquiryService.QuoteLine(
                                                            x.productId(),
                                                            x.grossAmount(),
                                                            x.productionQuantity()))
                                    .toList(),
                            result.expectedReadyAt(),
                            input.packagingReviewed(),
                            result.packagingTotal()));
            var mapper = new tools.jackson.databind.ObjectMapper();
            jdbc.update(
                    """
UPDATE occasion_enquiries SET estimated=?,original_estimate=?,quote_calculation=?::jsonb,
    extra_charges=?::jsonb,packing_finalized_at=NULL,balance_due_at=? WHERE id=?
""",
                    input.estimated(),
                    result.total(),
                    mapper.writeValueAsString(result),
                    mapper.writeValueAsString(result.extras()),
                    result.balanceDueAt() == null
                            ? null
                            : java.sql.Timestamp.from(result.balanceDueAt()),
                    id);
            if (input.estimated()) {
                jdbc.update(
                        "INSERT INTO"
                            + " occasion_enquiry_events(enquiry_id,actor,from_status,to_status,detail)"
                            + " VALUES(?,?,'QUOTED','QUOTED','Estimated weights; final invoice"
                            + " after packing at agreed rates')",
                        id,
                        staff);
            }
            if (enquiries.staffGet(env, branchId, id).gift() != null) {
                BigDecimal boxOnly =
                        result.packagingTotal()
                                .subtract(
                                        result.extras().stream()
                                                .map(
                                                        x ->
                                                                money(
                                                                        x.priceIncludingTax()
                                                                                .multiply(
                                                                                        BigDecimal
                                                                                                .valueOf(
                                                                                                        x
                                                                                                                .quantity()))))
                                                .reduce(BigDecimal.ZERO, BigDecimal::add));
                jdbc.update(
                        "UPDATE occasion_enquiries SET"
                            + " packaging_snapshot=jsonb_set(packaging_snapshot,'{approvedPackagingTotal}',to_jsonb(?::numeric))"
                            + " WHERE id=?",
                        boxOnly,
                        id);
            }
            return enquiries.staffGet(env, branchId, id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionQuoteCalculator.class,
                    "approve(ConsentEnvironment,long,UUID,String,Input)");
        }
    }

    /**
     * Treats a missing rebate as zero and otherwise requires a percentage from zero through 99 with
     * at most two decimal places.
     *
     * @param value the value supplied to this method
     * @return the {@code BigDecimal} result
     */
    static BigDecimal percent(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionQuoteCalculator.class, "percent(BigDecimal)");
        try {
            if (value == null) return BigDecimal.ZERO;
            if (value.signum() < 0
                    || value.compareTo(new BigDecimal("99")) > 0
                    || value.stripTrailingZeros().scale() > 2)
                invalid("Rebate must be 0–99%, with at most two decimal places.");
            return value;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionQuoteCalculator.class,
                    "percent(BigDecimal)");
        }
    }

    /**
     * Returns discounted information for occasion quote calculator.
     *
     * @param original the original supplied to this method
     * @param item the item supplied to this method
     * @param bulk the bulk supplied to this method
     * @return the value of {@code
     *     afterItem.subtract(money(afterItem.multiply(percent(bulk)).movePointLeft(2)))}
     */
    static BigDecimal discounted(BigDecimal original, BigDecimal item, BigDecimal bulk) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        OccasionQuoteCalculator.class,
                        "discounted(BigDecimal,BigDecimal,BigDecimal)");
        try {
            BigDecimal afterItem =
                    original.subtract(money(original.multiply(percent(item)).movePointLeft(2)));
            return afterItem.subtract(money(afterItem.multiply(percent(bulk)).movePointLeft(2)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    OccasionQuoteCalculator.class,
                    "discounted(BigDecimal,BigDecimal,BigDecimal)");
        }
    }

    /**
     * Rounds the monetary amount to two decimal places using half-up rounding.
     *
     * @param value the value supplied to this method
     * @return the value of {@code value.setScale(2, RoundingMode.HALF_UP)}
     */
    static BigDecimal money(BigDecimal value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionQuoteCalculator.class, "money(BigDecimal)");
        try {
            return value.setScale(2, RoundingMode.HALF_UP);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionQuoteCalculator.class, "money(BigDecimal)");
        }
    }

    /**
     * Rejects the request with an HTTP BAD_REQUEST response.
     *
     * @param message the message supplied to this method
     */
    private static void invalid(String message) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OccasionQuoteCalculator.class, "invalid(String)");
        try {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OccasionQuoteCalculator.class, "invalid(String)");
        }
    }
}
