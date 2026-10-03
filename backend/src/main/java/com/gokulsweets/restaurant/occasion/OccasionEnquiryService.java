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
import java.math.RoundingMode;
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
    private final com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox notifications;
    private final com.gokulsweets.restaurant.staff.notification.StaffOrderAlerts staffAlerts;

    public record Item(@Positive long productId, @DecimalMin("0.001") @Digits(integer = 9, fraction = 3)
                       BigDecimal quantity, @NotNull Unit unit, String productName, String productionUnit, BigDecimal suggestedProductionQuantity, @DecimalMin("0") @Digits(integer=9,fraction=0) BigDecimal supplementalGrams) {
        public Item(long productId,BigDecimal quantity,Unit unit,String productName,String productionUnit,BigDecimal suggestedProductionQuantity){this(productId,quantity,unit,productName,productionUnit,suggestedProductionQuantity,null);}
        public Item(long productId, BigDecimal quantity, Unit unit, String productName) {this(productId,quantity,unit,productName,null,null,null);}
    }
    public enum Unit { PIECE, GRAM }
    public enum Fulfilment { PICKUP, DELIVERY_REQUEST }
    public record Request(@Positive long branchId, @NotBlank @Size(max = 80) String occasionType,
                          @NotNull LocalDate serviceDate, @Min(1) @Max(10000) int guestCount,
                          @NotNull Fulfilment fulfilment, @Size(max = 500) String deliveryAddress,
                          @Size(max = 1000) String notes,
                          @NotEmpty @Size(max = 30) List<@Valid Item> items, OccasionCatalogue.GiftRequest gift, @Size(max=30) List<OccasionCatalogue.PackingGroup> packingGroups) {
        public Request(long branchId,String occasionType,LocalDate serviceDate,int guestCount,Fulfilment fulfilment,String deliveryAddress,String notes,List<Item> items,OccasionCatalogue.GiftRequest gift) {this(branchId,occasionType,serviceDate,guestCount,fulfilment,deliveryAddress,notes,items,gift,null);}
        public Request(long branchId, String occasionType, LocalDate serviceDate, int guestCount, Fulfilment fulfilment, String deliveryAddress, String notes, List<Item> items) {
            this(branchId, occasionType, serviceDate, guestCount, fulfilment, deliveryAddress, notes, items, null, null);
        }
    }
    public record Quote(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal deposit,
                        @NotNull Instant expiresAt, Instant balanceDueAt,
                        @NotBlank @Size(max = 500) String terms,
                        @NotEmpty List<@Valid QuoteLine> lines, LocalDateTime expectedReadyAt,
                        boolean packagingReviewed, BigDecimal packagingTotal) {
        public Quote(BigDecimal amount, BigDecimal deposit, Instant expiresAt, Instant balanceDueAt, String terms, List<QuoteLine> lines, LocalDateTime expectedReadyAt) {
            this(amount,deposit,expiresAt,balanceDueAt,terms,lines,expectedReadyAt,false,null);
        }
        public Quote(BigDecimal amount, BigDecimal deposit, Instant expiresAt, Instant balanceDueAt,
                     String terms, List<QuoteLine> lines) {
            this(amount, deposit, expiresAt, balanceDueAt, terms, lines, null);
        }
    }
    public record QuoteLine(@Positive long productId,
                            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal grossAmount, BigDecimal productionQuantity) {
        public QuoteLine(long productId, BigDecimal grossAmount) {this(productId, grossAmount, null);}
    }
    public record PricedLine(long productId, String productName, BigDecimal grossAmount,
                             BigDecimal subtotal, BigDecimal taxAmount, BigDecimal cgstRate,
                             BigDecimal sgstRate, String hsnSacCode) {}
    public record Summary(UUID id, long branchId, String occasionType, LocalDate serviceDate, int guestCount,
                          Fulfilment fulfilment, String status, BigDecimal quotedAmount,
                          BigDecimal depositAmount, BigDecimal paidAmount, String quoteTerms, Instant quoteExpiresAt,
                          Instant createdAt, String nextStep, String customerPhone, String deliveryAddress,
                          String notes, Instant balanceDueAt, Instant holdExpiresAt,
                          Long pickupSlotId, List<Item> items, List<PricedLine> pricedLines, String orderNumber,
                          boolean balancePaymentOpen, List<ProductionLine> productionPlan, CancellationReview cancellationReview, OccasionCatalogue.GiftSnapshot gift, boolean estimated, BigDecimal originalEstimate,
                          Instant packingFinalizedAt,int packingRevision,BigDecimal creditReviewAmount,
                          List<OccasionQuoteCalculator.Extra> extraCharges,OccasionQuoteCalculator.Calculation calculation,List<OccasionCatalogue.PackedGroup> packingGroups) {}
    public record CancellationReview(BigDecimal paidAmount, String reason, String state) {}
    public record ProductionLine(long productId, BigDecimal quantity, String unit, LocalDateTime expectedReadyAt, String state, BigDecimal readyQuantity, long readinessRevision) {}

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
                || i.quantity().compareTo(new BigDecimal("100000000")) > 0 || i.unit() == null
                || i.supplementalGrams()!=null && (i.supplementalGrams().signum()<0 || i.supplementalGrams().compareTo(new BigDecimal("100000000"))>0 || i.supplementalGrams().stripTrailingZeros().scale()>0 || i.unit()!=Unit.PIECE)
                || i.unit() == Unit.PIECE && i.quantity().stripTrailingZeros().scale() > 0)
                || input.items().stream().map(Item::productId).distinct().count() != input.items().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose distinct products and valid quantities.");
        // Serialize identical submissions so concurrent tabs cannot create duplicate requests.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))", subject.toString());
        String requestHash = requestHash(input);
        List<UUID> duplicate = jdbc.query("""
                SELECT id FROM occasion_enquiries WHERE environment = ? AND subject_id = ?
                  AND request_hash = ? AND created_at >= ? ORDER BY created_at DESC LIMIT 1
                """, (rs, row) -> (UUID) rs.getObject(1), environment.name(), subject, requestHash,
                Timestamp.from(clock.instant().minus(Duration.ofMinutes(15))));
        if (!duplicate.isEmpty()) return get(environment, subject, duplicate.getFirst());
        // Hold availability through insertion, serializing with the operational toggle.
        if (!jdbc.query("SELECT active AND operational FROM branches WHERE id = ? FOR SHARE",
                (rs, row) -> rs.getBoolean(1), input.branchId()).stream().findFirst().orElse(false)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch is unavailable.");
        var catalogue = new OccasionCatalogue(jdbc, features, clock);
        var options = catalogue.catalogue(input.branchId(), false).sweets();
        for (Item item : input.items()) {
            var sweet = options.stream().filter(p -> p.id() == item.productId()).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "A selected sweet is unavailable for occasion booking."));
            if(item.supplementalGrams()!=null && item.supplementalGrams().signum()>0 && !"WEIGHT".equals(sweet.saleMode()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Additional kg requires a weight-priced item.");
            if (input.serviceDate().isBefore(LocalDate.now(clock.withZone(IST)).plusDays(sweet.leadDays()))
                || item.unit() == Unit.GRAM && (!"WEIGHT".equals(sweet.saleMode()) || item.quantity().stripTrailingZeros().scale() > 0))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check the sweet's lead time and quantity unit.");
        }
        if(input.gift()!=null && input.packingGroups()!=null&&!input.packingGroups().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use one packing plan per request.");
        var groups=catalogue.validateGroups(input.branchId(),input.serviceDate(),input.items(),input.packingGroups());
        var gift = catalogue.validateGift(input.branchId(), input.serviceDate(), input.items(), input.gift());
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO occasion_enquiries (id, environment, subject_id, branch_id, occasion_type,
                    service_date, guest_count, fulfilment, delivery_address, notes, request_hash, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'REQUESTED')
                """, id, environment.name(), subject, input.branchId(), input.occasionType().trim(),
                Date.valueOf(input.serviceDate()), input.guestCount(), input.fulfilment().name(),
                input.fulfilment() == Fulfilment.PICKUP ? null : input.deliveryAddress().trim(), input.notes(), requestHash);
        for (Item item : input.items()) jdbc.update("""
                INSERT INTO occasion_enquiry_items (enquiry_id, product_id, requested_quantity, unit,supplemental_grams)
                VALUES (?, ?, ?, ?,?)
                """, id, item.productId(), item.quantity(), item.unit().name(),item.supplementalGrams()==null?BigDecimal.ZERO:item.supplementalGrams());
        event(id, "customer", null, "REQUESTED", null);
        if (gift != null) jdbc.update("UPDATE occasion_enquiries SET packaging_snapshot=?::jsonb WHERE id=?", new tools.jackson.databind.ObjectMapper().writeValueAsString(gift), id);
        jdbc.update("UPDATE occasion_enquiries SET packing_groups=?::jsonb WHERE id=?",new tools.jackson.databind.ObjectMapper().writeValueAsString(groups),id);
        staffAlerts.occasionChanged(id,false);
        return get(environment, subject, id);
    }

    @Transactional(readOnly = true)
    public List<Summary> customerList(ConsentEnvironment environment, UUID subject) {
        return jdbc.query("""
                SELECT * FROM occasion_enquiries WHERE environment = ? AND subject_id = ? ORDER BY created_at DESC,id DESC LIMIT 100
                """, (rs, row) -> map(rs), environment.name(), subject);
    }

    @Transactional(readOnly = true)
    public List<Summary> customerList(ConsentEnvironment environment,UUID subject,UUID before) {
        if(before==null)return customerList(environment,subject);
        return jdbc.query("""
            SELECT e.* FROM occasion_enquiries e
            WHERE e.environment=? AND e.subject_id=? AND (e.created_at,e.id)<
              (SELECT created_at,id FROM occasion_enquiries WHERE id=? AND environment=? AND subject_id=?)
            ORDER BY e.created_at DESC,e.id DESC LIMIT 100
            """,(rs,row)->map(rs),environment.name(),subject,before,environment.name(),subject);
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
                SELECT * FROM occasion_enquiries WHERE environment = ? AND branch_id = ? ORDER BY created_at DESC,id DESC LIMIT 100
                """, (rs, row) -> map(rs), environment.name(), branchId);
    }

    @Transactional(readOnly=true)
    public List<Summary> staffList(ConsentEnvironment environment,long branchId,LocalDate date,UUID before) {
        if(before==null)return jdbc.query("SELECT * FROM occasion_enquiries WHERE environment=? AND branch_id=? AND service_date=? ORDER BY created_at DESC,id DESC LIMIT 50",(rs,n)->map(rs),environment.name(),branchId,date);
        return jdbc.query("SELECT e.* FROM occasion_enquiries e JOIN occasion_enquiries cursor ON cursor.id=? AND cursor.environment=e.environment AND cursor.branch_id=e.branch_id AND cursor.service_date=e.service_date WHERE e.environment=? AND e.branch_id=? AND e.service_date=? AND (e.created_at,e.id)<(cursor.created_at,cursor.id) ORDER BY e.created_at DESC,e.id DESC LIMIT 50",(rs,n)->map(rs),before,environment.name(),branchId,date);
    }

    @Transactional
    public Summary quote(ConsentEnvironment environment, long branchId, UUID id, String staff, Quote quote) {
        if (quote.deposit().compareTo(quote.amount()) > 0 || !quote.expiresAt().isAfter(clock.instant())
                || quote.expiresAt().isAfter(clock.instant().plus(Duration.ofDays(14))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quote amount or expiry is invalid.");
        if (quote.deposit().signum() <= 0
                || quote.deposit().compareTo(quote.amount()) < 0 && (quote.balanceDueAt() == null
                || !quote.balanceDueAt().isAfter(quote.expiresAt()))
                || quote.deposit().compareTo(quote.amount()) == 0 && quote.balanceDueAt() != null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a positive deposit and a balance deadline after the quote expires.");
        String before = lockedStatus(environment, branchId, id);
        LocalDate serviceDate = jdbc.query("""
                SELECT service_date FROM occasion_enquiries WHERE id = ? AND environment = ? AND branch_id = ?
                """, rs -> rs.next() ? rs.getDate(1).toLocalDate() : null,
                id, environment.name(), branchId);
        if (quote.balanceDueAt() != null && (serviceDate == null
                || !quote.balanceDueAt().isBefore(serviceDate.atStartOfDay(IST).toInstant())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Balance must be due before the event date in India.");
        if (!"REQUESTED".equals(before) && !"QUOTED".equals(before))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an open request can be quoted.");
        if ("QUOTED".equals(before) && expired(environment, branchId, id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This quote expired; ask for a new enquiry.");
        if (quote.lines() == null || quote.lines().isEmpty() || quote.lines().size() > 30
                || quote.lines().stream().anyMatch(line -> line == null || line.grossAmount() == null
                    || line.grossAmount().signum() <= 0 || line.grossAmount().scale() > 2)
                || quote.lines().stream().map(QuoteLine::productId).distinct().count() != quote.lines().size()
                || quote.lines().stream().map(QuoteLine::grossAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .compareTo(quote.amount()) != 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item prices must add up to the quote amount.");
        Integer requestedCount = jdbc.queryForObject("SELECT count(*) FROM occasion_enquiry_items WHERE enquiry_id = ?",
                Integer.class, id);
        if (requestedCount == null || requestedCount != quote.lines().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price every requested product exactly once.");
        String giftJson=jdbc.queryForObject("SELECT packaging_snapshot::text FROM occasion_enquiries WHERE id=?",String.class,id);
        boolean groupPacking=Boolean.TRUE.equals(jdbc.queryForObject("SELECT jsonb_array_length(packing_groups)>0 FROM occasion_enquiries WHERE id=?",Boolean.class,id));
        Boolean requiresDedicated = jdbc.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM occasion_enquiry_items i
              JOIN occasion_enquiries e ON e.id=i.enquiry_id JOIN products p ON p.id=i.product_id
              LEFT JOIN branch_products bp ON bp.branch_id=e.branch_id AND bp.product_id=i.product_id
              WHERE i.enquiry_id=? AND (bp.occasion_only OR (p.sale_mode='WEIGHT' AND i.unit='PIECE')))
            """, Boolean.class,id);
        if(!features.isOccasionBulkProduction() && (giftJson!=null || groupPacking || Boolean.TRUE.equals(requiresDedicated)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Dedicated bulk production must be enabled before approving this occasion request.");
        if(giftJson!=null || groupPacking) {
            if(!quote.packagingReviewed() || quote.packagingTotal()==null || quote.packagingTotal().signum()<0
                || quote.packagingTotal().scale()>2 || quote.packagingTotal().compareTo(quote.amount())>=0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Review physical box fit, branding and lead time; approve the packaging total included in item prices.");
            if(giftJson!=null) jdbc.update("UPDATE occasion_enquiries SET packaging_snapshot=jsonb_set(packaging_snapshot,'{approvedPackagingTotal}',to_jsonb(?::numeric)) WHERE id=?",quote.packagingTotal(),id);
        }
        jdbc.update("DELETE FROM occasion_quote_lines WHERE enquiry_id = ?", id);
        boolean collectTax=Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM tax_collection_settings WHERE id=1",Boolean.class));
        for (QuoteLine line : quote.lines()) {
            var tax = jdbc.query("""
                    SELECT tc.cgst_rate, tc.sgst_rate, tc.hsn_sac_code,
                           p.name, p.sale_mode, i.requested_quantity, i.unit, bp.occasion_piece_grams,i.supplemental_grams
                    FROM occasion_enquiry_items i JOIN products p ON p.id = i.product_id
                    JOIN tax_categories tc ON tc.id = p.tax_category_id AND tc.active
                    JOIN occasion_enquiries e ON e.id=i.enquiry_id
                    LEFT JOIN branch_products bp ON bp.product_id=p.id AND bp.branch_id=e.branch_id
                    WHERE i.enquiry_id = ? AND i.product_id = ?
                    """, rs -> rs.next() ? new Object[]{rs.getBigDecimal(1), rs.getBigDecimal(2),
                            rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6), rs.getString(7), rs.getBigDecimal(8),rs.getBigDecimal(9)} : null,
                    id, line.productId());
            if (tax == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A product has no active tax category. Configure it before quoting.");
            BigDecimal production = line.productionQuantity();
            boolean weight = "WEIGHT".equals(tax[4]);
            if (production == null) production = weight && "PIECE".equals(tax[6])
                ? tax[7] == null ? null : ((BigDecimal)tax[5]).multiply((BigDecimal)tax[7]).add((BigDecimal)tax[8]) : (BigDecimal)tax[5];
            if (production == null || production.signum() <= 0 || production.stripTrailingZeros().scale() > 0
                || production.compareTo(new BigDecimal("100000000")) > 0
                || weight && "GRAM".equals(tax[6]) && production.compareTo((BigDecimal)tax[5])<0
                || ((BigDecimal)tax[8]).signum()>0 && production.compareTo((BigDecimal)tax[8])<=0
                || !weight && production.compareTo((BigDecimal)tax[5]) != 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approve a whole-gram production quantity for piece-based weight sweets before quoting. Unit sweets must retain the requested piece count.");
            jdbc.update("UPDATE occasion_enquiry_items SET approved_quantity=?,approved_unit=? WHERE enquiry_id=? AND product_id=?",
                production, weight ? "GRAM" : "PIECE", id, line.productId());
            if(!collectTax){tax[0]=BigDecimal.ZERO;tax[1]=BigDecimal.ZERO;}
            BigDecimal rate = ((BigDecimal) tax[0]).add((BigDecimal) tax[1]);
            BigDecimal subtotal = line.grossAmount().divide(BigDecimal.ONE.add(rate.movePointLeft(2)),
                    2, RoundingMode.HALF_UP);
            jdbc.update("""
                    INSERT INTO occasion_quote_lines(enquiry_id, product_id, product_name, sale_mode,
                        quantity, weight_grams, gross_amount, subtotal, tax_amount,
                        cgst_rate, sgst_rate, hsn_sac_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, line.productId(), tax[3], tax[4],
                    weight ? 1 : production.intValueExact(),
                    weight ? production.intValueExact() : null,
                    line.grossAmount(), subtotal, line.grossAmount().subtract(subtotal), tax[0], tax[1], tax[2]);
        }
        jdbc.update("""
                UPDATE occasion_enquiries SET status = 'QUOTED', quoted_amount = ?, deposit_amount = ?,
                    quote_terms = ?, quote_expires_at = ?, balance_due_at = ?,
                    estimated=FALSE,quote_calculation=NULL,extra_charges='[]',original_estimate=NULL,estimate_accepted_at=NULL,packing_finalized_at=NULL,
                    updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, quote.amount(), quote.deposit(), quote.terms().trim(), Timestamp.from(quote.expiresAt()),
                quote.balanceDueAt() == null ? null : Timestamp.from(quote.balanceDueAt()), id);
        if (features.isOccasionBulkProduction()) {
            String fulfilment = jdbc.queryForObject("SELECT fulfilment FROM occasion_enquiries WHERE id = ?", String.class, id);
            if ("PICKUP".equals(fulfilment)) {
                if (quote.expectedReadyAt() == null || !quote.expectedReadyAt().toLocalDate().equals(serviceDate)
                        || !quote.expectedReadyAt().isAfter(LocalDateTime.now(clock.withZone(IST))))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Enter a kitchen-ready time on the fulfilment date in IST after reviewing procurement and existing commitments.");
                jdbc.update("DELETE FROM occasion_production_allocations WHERE enquiry_id = ?", id);
                jdbc.update("""
                        INSERT INTO occasion_production_allocations(enquiry_id, product_id, quantity, unit,
                            expected_ready_at, state, approved_by)
                        SELECT enquiry_id, product_id, approved_quantity, approved_unit, ?, 'PLANNED', ?
                        FROM occasion_enquiry_items WHERE enquiry_id = ?
                        """, Timestamp.valueOf(quote.expectedReadyAt()), staff, id);
            }
        }
        event(id, staff, before, "QUOTED", quote.terms().trim());
        return staffGet(environment, branchId, id);
    }

    @Transactional
    public Summary decline(ConsentEnvironment environment, long branchId, UUID id, String staff, String reason) {
        if(reason==null||reason.isBlank()||reason.trim().length()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Explain why this request cannot be fulfilled.");
        String before = lockedStatus(environment, branchId, id);
        if (!"REQUESTED".equals(before) && !"QUOTED".equals(before))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is no longer open.");
        jdbc.update("UPDATE occasion_enquiries SET status = 'DECLINED', quote_terms=?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", reason.trim(),id);
        jdbc.update("UPDATE occasion_production_allocations SET state = 'RELEASED', updated_at = CURRENT_TIMESTAMP WHERE enquiry_id = ?", id);
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

    public Summary staffGet(ConsentEnvironment environment, long branchId, UUID id) {
        return jdbc.query("SELECT * FROM occasion_enquiries WHERE id = ? AND environment = ? AND branch_id = ?",
                rs -> rs.next() ? map(rs) : null, id, environment.name(), branchId);
    }

    private void event(UUID id, String actor, String before, String after, String detail) {
        jdbc.update("INSERT INTO occasion_enquiry_events (enquiry_id, actor, from_status, to_status, detail) VALUES (?, ?, ?, ?, ?)",
                id, actor, before, after, detail);
        notifications.occasionDecisionChanged(id);
        if("QUOTED".equals(after)||"DECLINED".equals(after))staffAlerts.occasionReviewed(id);
    }

    private static String requestHash(Request input) {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, Long.toString(input.branchId()));
        appendField(canonical, input.occasionType().trim());
        appendField(canonical, input.serviceDate().toString());
        appendField(canonical, Integer.toString(input.guestCount()));
        appendField(canonical, input.fulfilment().name());
        appendField(canonical, input.deliveryAddress() == null ? "" : input.deliveryAddress().trim());
        appendField(canonical, new tools.jackson.databind.ObjectMapper().writeValueAsString(input.gift()));
        appendField(canonical,new tools.jackson.databind.ObjectMapper().writeValueAsString(input.packingGroups()));
        appendField(canonical, input.notes() == null ? "" : input.notes().trim());
        input.items().stream().sorted(Comparator.comparingLong(Item::productId))
                .forEach(item -> {
                    appendField(canonical, Long.toString(item.productId()));
                    appendField(canonical, item.quantity().stripTrailingZeros().toPlainString());
                    appendField(canonical,item.supplementalGrams()==null?"0":item.supplementalGrams().stripTrailingZeros().toPlainString());
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
            case "QUOTED" -> features.isOccasionPayments() && "PICKUP".equals(rs.getString("fulfilment"))
                    ? "Review your quote, choose a pickup time and pay the deposit to reserve production. This is not yet confirmed."
                    : "A manager has prepared a quote. Contact the branch to review it; no booking is confirmed.";
            case "EXPIRED" -> "This quote has expired. Ask the branch for a new quote.";
            case "DECLINED" -> "The branch cannot take this request. Please choose another date or branch.";
            case "PAYMENT_PENDING", "HELD" -> "Your pickup and inventory are held briefly while the deposit is pending. Check payment status before retrying.";
            case "PAID" -> rs.getTimestamp("balance_due_at") != null
                    && !rs.getTimestamp("balance_due_at").toInstant().isAfter(clock.instant())
                    ? "The balance deadline passed. Your deposit is recorded, but this booking is not confirmed. Contact the branch before making another payment."
                    : "Your deposit is verified and your items are committed. Pay the remaining balance by its deadline to confirm.";
            case "CANCELLED" -> "Your booking is cancelled. The amount already paid needs branch finance review; no refund is confirmed yet. Contact the branch.";
            case "CONFIRMED" -> "The required payments are verified and this pickup is confirmed.";
            default -> "The branch will confirm the next step. This is not a confirmed booking.";
        };
        final String currentStatus = status;
        return new Summary((UUID) rs.getObject("id"), rs.getLong("branch_id"), rs.getString("occasion_type"),
                rs.getDate("service_date").toLocalDate(), rs.getInt("guest_count"),
                Fulfilment.valueOf(rs.getString("fulfilment")), status, rs.getBigDecimal("quoted_amount"),
                rs.getBigDecimal("deposit_amount"), rs.getBigDecimal("paid_amount"), rs.getString("quote_terms"), expiry,
                rs.getTimestamp("created_at").toInstant(), next,
                customers.verifiedPhone(ConsentEnvironment.valueOf(rs.getString("environment")),
                        (UUID) rs.getObject("subject_id")).orElse(""),
                rs.getString("delivery_address"), rs.getString("notes"),
                rs.getTimestamp("balance_due_at") == null ? null : rs.getTimestamp("balance_due_at").toInstant(),
                rs.getTimestamp("hold_expires_at") == null ? null : rs.getTimestamp("hold_expires_at").toInstant(),
                rs.getObject("pickup_slot_id", Long.class),
                jdbc.query("""
                        SELECT i.product_id, i.requested_quantity, i.unit, p.name,
                               CASE WHEN p.sale_mode='WEIGHT' THEN 'GRAM' ELSE 'PIECE' END,
                               CASE WHEN p.sale_mode='WEIGHT' AND i.unit='PIECE' THEN i.requested_quantity*bp.occasion_piece_grams+i.supplemental_grams ELSE i.requested_quantity END,i.supplemental_grams
                        FROM occasion_enquiry_items i JOIN products p ON p.id = i.product_id
                        JOIN occasion_enquiries e ON e.id=i.enquiry_id
                        LEFT JOIN branch_products bp ON bp.branch_id=e.branch_id AND bp.product_id=i.product_id
                        WHERE i.enquiry_id = ? ORDER BY p.name
                        """,
                        (items, row) -> new Item(items.getLong(1), items.getBigDecimal(2),
                                Unit.valueOf(items.getString(3)), items.getString(4),items.getString(5),items.getBigDecimal(6),items.getBigDecimal(7)),
                        (UUID) rs.getObject("id")),
                jdbc.query("""
                        SELECT q.product_id, q.product_name, q.gross_amount, q.subtotal, q.tax_amount,
                               q.cgst_rate, q.sgst_rate, q.hsn_sac_code
                        FROM occasion_quote_lines q JOIN products p ON p.id = q.product_id
                        WHERE q.enquiry_id = ? ORDER BY p.name
                        """, (lines, row) -> new PricedLine(lines.getLong(1), lines.getString(2),
                        lines.getBigDecimal(3), lines.getBigDecimal(4), lines.getBigDecimal(5),
                        lines.getBigDecimal(6), lines.getBigDecimal(7), lines.getString(8)),
                        (UUID) rs.getObject("id")),
                rs.getObject("order_id") == null ? null : jdbc.queryForObject(
                        "SELECT order_number FROM orders WHERE id = ?", String.class, rs.getLong("order_id")),
                "PAID".equals(status) && rs.getTimestamp("balance_due_at") != null
                        && rs.getTimestamp("balance_due_at").toInstant().isAfter(clock.instant())
                        && (!rs.getBoolean("estimated") || rs.getTimestamp("packing_finalized_at")!=null),
                jdbc.query("SELECT product_id, quantity, unit, expected_ready_at, state, ready_quantity, readiness_revision FROM occasion_production_allocations WHERE enquiry_id = ? ORDER BY product_id",
                        (plan, row) -> new ProductionLine(plan.getLong(1), plan.getBigDecimal(2), plan.getString(3),
                                plan.getTimestamp(4).toLocalDateTime(), "EXPIRED".equals(currentStatus) && "PLANNED".equals(plan.getString(5))
                                ? "RELEASED" : plan.getString(5), plan.getBigDecimal(6), plan.getLong(7)), (UUID) rs.getObject("id")),
                jdbc.query("SELECT paid_amount, reason, state FROM occasion_cancellation_reviews WHERE enquiry_id = ?",
                        review -> review.next() ? new CancellationReview(review.getBigDecimal(1), review.getString(2), review.getString(3)) : null,
                        (UUID) rs.getObject("id")),
                rs.getString("packaging_snapshot") == null ? null : new tools.jackson.databind.ObjectMapper().readValue(rs.getString("packaging_snapshot"), OccasionCatalogue.GiftSnapshot.class),
                rs.getBoolean("estimated"),rs.getBigDecimal("original_estimate"),
                rs.getTimestamp("packing_finalized_at")==null?null:rs.getTimestamp("packing_finalized_at").toInstant(),
                rs.getInt("packing_revision"),rs.getBigDecimal("credit_review_amount"),
                new tools.jackson.databind.ObjectMapper().readValue(rs.getString("extra_charges"),new tools.jackson.core.type.TypeReference<List<OccasionQuoteCalculator.Extra>>() {}),
                rs.getString("quote_calculation")==null?null:new tools.jackson.databind.ObjectMapper().readValue(rs.getString("quote_calculation"),OccasionQuoteCalculator.Calculation.class),
                new tools.jackson.databind.ObjectMapper().readValue(rs.getString("packing_groups"),new tools.jackson.core.type.TypeReference<List<OccasionCatalogue.PackedGroup>>() {}));
    }

    private static <T> T throwNotFound() { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
