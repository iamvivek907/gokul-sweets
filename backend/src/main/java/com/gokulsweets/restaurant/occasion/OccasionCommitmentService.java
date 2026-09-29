package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.inventory.config.InventoryProperties;
import com.gokulsweets.restaurant.inventory.enums.InventoryControlMode;
import com.gokulsweets.restaurant.inventory.model.CreateInventoryHoldCommand;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.exception.InventoryNotFoundException;
import com.gokulsweets.restaurant.inventory.repository.BranchInventoryPolicyRepository;
import com.gokulsweets.restaurant.inventory.service.InventoryReservationService;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePeClient;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePeProperties;
import com.gokulsweets.restaurant.pickup.service.PickupSlotReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Provider payment is reconciled to a real inventory and pickup hold before an occasion is confirmed. */
@Service
@RequiredArgsConstructor
public class OccasionCommitmentService {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(10);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final InventoryReservationService inventory;
    private final BranchInventoryPolicyRepository policies;
    private final PickupSlotReservationService pickupSlots;
    private final InventoryProperties inventoryProperties;
    private final EnhancementProperties features;
    private final PhonePeClient phonePe;
    private final PhonePeProperties phonePeProperties;
    private final OccasionOrderFinalizer orders;
    private final Clock clock;

    public record Checkout(UUID attemptId, String stage, String status, BigDecimal amount,
                           Instant expiresAt, String paymentUrl) {}
    private record Enquiry(UUID id, ConsentEnvironment environment, UUID subject, long branchId,
                           LocalDate date, String fulfilment, String status, BigDecimal quote,
                           BigDecimal deposit, BigDecimal paid, Instant quoteExpiry,
                           Instant holdExpiry, Instant balanceDue, Long slotId) {}
    private record Attempt(UUID id, UUID enquiryId, ConsentEnvironment environment, String stage,
                           String status, BigDecimal amount, String merchantOrderId, Instant expiresAt,
                           String url) {}
    private record HeldItem(String key, long productId) {}
    private record Start(Attempt attempt, boolean newAttempt) {}

    public Checkout beginDeposit(ConsentEnvironment environment, UUID subject, UUID id, long pickupSlotId) {
        requireEnabled();
        Start start = transactions.execute(state -> {
            Enquiry enquiry = lockEnquiry(id);
            requireOwner(enquiry, environment, subject);
            if ("PAYMENT_PENDING".equals(enquiry.status()) || "HELD".equals(enquiry.status())) {
                Attempt pending = pendingAttempt(id, "DEPOSIT");
                if (pending != null && pending.expiresAt().isAfter(clock.instant())) return new Start(pending, false);
                throw conflict("The deposit window ended. Contact the branch for a new quote.");
            }
            if (!"QUOTED".equals(enquiry.status()) || enquiry.quoteExpiry() == null
                    || !enquiry.quoteExpiry().isAfter(clock.instant()))
                throw conflict("This quote is no longer open. Ask the branch for a new quote.");
            if (!"PICKUP".equals(enquiry.fulfilment()))
                throw conflict("Delivery needs separate coverage and capacity approval. No delivery payment is available.");
            if (enquiry.deposit() == null || enquiry.deposit().signum() <= 0
                    || enquiry.quote() == null || enquiry.deposit().compareTo(enquiry.quote()) > 0)
                throw conflict("The branch must issue a payable quote with a valid deposit.");
            BigDecimal priced = jdbc.queryForObject("SELECT COALESCE(sum(gross_amount), 0) FROM occasion_quote_lines WHERE enquiry_id = ?",
                    BigDecimal.class, id);
            Integer pricedCount = jdbc.queryForObject("SELECT count(*) FROM occasion_quote_lines WHERE enquiry_id = ?",
                    Integer.class, id);
            Integer itemCount = jdbc.queryForObject("SELECT count(*) FROM occasion_enquiry_items WHERE enquiry_id = ?",
                    Integer.class, id);
            if (priced == null || priced.compareTo(enquiry.quote()) != 0 || !Objects.equals(pricedCount, itemCount))
                throw conflict("This quote needs item prices and tax reviewed by the branch before payment.");
            if (inventoryProperties.getTemporaryHoldMinutes() < 12 || !inventoryProperties.isEnforcementEnabled())
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Occasion inventory holds are not configured.");
            Instant serviceStart = jdbc.query("""
                    SELECT slot_date, start_time FROM pickup_slots
                    WHERE id = ? AND branch_id = ? AND slot_date = ? AND active
                    """, rs -> rs.next() ? LocalDateTime.of(rs.getDate(1).toLocalDate(),
                    rs.getTime(2).toLocalTime()).atZone(IST).toInstant() : null,
                    pickupSlotId, enquiry.branchId(), java.sql.Date.valueOf(enquiry.date()));
            if (serviceStart == null || !serviceStart.isAfter(clock.instant()))
                throw conflict("Choose an available future pickup time for this branch and date.");

            // The standard order path takes its slot lock before allocation locks; keep that order.
            try {
                pickupSlots.reserveNormalCapacity(pickupSlotId);
            } catch (IllegalStateException unavailable) {
                throw conflict("That pickup time is full. Choose another time.");
            }
            List<Object[]> requested = jdbc.query("""
                    SELECT bp.id, i.product_id, i.requested_quantity FROM occasion_enquiry_items i
                    JOIN branch_products bp ON bp.branch_id = ? AND bp.product_id = i.product_id
                    JOIN products p ON p.id = bp.product_id
                    WHERE i.enquiry_id = ? AND bp.available AND p.active ORDER BY bp.id
                    """, (rs, row) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3)},
                    enquiry.branchId(), id);
            Integer count = jdbc.queryForObject("SELECT count(*) FROM occasion_enquiry_items WHERE enquiry_id = ?",
                    Integer.class, id);
            if (requested.isEmpty() || requested.size() != count) throw conflict("A quoted product is no longer on this branch's menu.");
            Instant earliestHoldExpiry = null;
            for (Object[] item : requested) {
                long branchProductId = (long) item[0];
                long productId = (long) item[1];
                var policy = policies.findByBranchProductId(branchProductId)
                        .orElseThrow(() -> conflict("An item has no approved inventory policy."));
                if (policy.getControlMode() == InventoryControlMode.SLOT_CAPACITY
                        || enquiry.date().isAfter(LocalDate.now(clock.withZone(IST))
                                .plusDays(policy.getBookingHorizonDays()))
                        || serviceStart.isBefore(clock.instant().plus(Duration.ofMinutes(policy.getProductionLeadMinutes()))))
                    throw conflict("The date or quantity needs a new production review.");
                String key = "OCC-" + id + "-" + productId;
                var hold = inventory.createHold(new CreateInventoryHoldCommand(key, null,
                        branchProductId, enquiry.date(), (BigDecimal) item[2]));
                Instant expires = hold.getExpiresAt().atZone(IST).toInstant();
                if (earliestHoldExpiry == null || expires.isBefore(earliestHoldExpiry)) earliestHoldExpiry = expires;
                jdbc.update("INSERT INTO occasion_hold_items(enquiry_id, product_id, reservation_key) VALUES (?, ?, ?)",
                        id, productId, key);
            }
            Instant paymentExpiry = clock.instant().plus(PAYMENT_WINDOW);
            if (earliestHoldExpiry == null || !earliestHoldExpiry.isAfter(paymentExpiry.plusSeconds(30)))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The inventory hold is shorter than the payment window.");
            UUID attemptId = UUID.randomUUID();
            String merchantId = "GKS-" + environment.name() + "-OCC-" + attemptId;
            jdbc.update("""
                    INSERT INTO occasion_payment_attempts(id, enquiry_id, environment, stage, status,
                        amount, merchant_order_id, expires_at)
                    VALUES (?, ?, ?, 'DEPOSIT', 'PENDING', ?, ?, ?)
                    """, attemptId, id, environment.name(), enquiry.deposit(), merchantId,
                    Timestamp.from(paymentExpiry));
            jdbc.update("""
                    UPDATE occasion_enquiries SET status = 'PAYMENT_PENDING', pickup_slot_id = ?,
                        hold_expires_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, pickupSlotId, Timestamp.from(earliestHoldExpiry), id);
            event(id, "customer", "QUOTED", "PAYMENT_PENDING", "Deposit checkout started");
            return new Start(new Attempt(attemptId, id, environment, "DEPOSIT", "PENDING",
                    enquiry.deposit(), merchantId, paymentExpiry, null), true);
        });
        return start.newAttempt() ? startProvider(start.attempt()) : response(start.attempt());
    }

    public Checkout beginBalance(ConsentEnvironment environment, UUID subject, UUID id) {
        requireEnabled();
        Start start = transactions.execute(state -> {
            Enquiry enquiry = lockEnquiry(id);
            requireOwner(enquiry, environment, subject);
            if (!"PAID".equals(enquiry.status()) || enquiry.deposit() == null
                    || enquiry.paid().compareTo(enquiry.deposit()) != 0
                    || enquiry.quote().compareTo(enquiry.paid()) <= 0)
                throw conflict("The deposit must be verified before paying the balance.");
            if (enquiry.balanceDue() != null && !enquiry.balanceDue().isAfter(clock.instant()))
                throw conflict("The balance deadline passed. Contact the branch to review this booking.");
            Attempt existing = pendingAttempt(id, "BALANCE");
            if (existing != null && existing.expiresAt().isAfter(clock.instant())) return new Start(existing, false);
            if (existing != null) jdbc.update("UPDATE occasion_payment_attempts SET status = 'EXPIRED' WHERE id = ?", existing.id());
            UUID attemptId = UUID.randomUUID();
            Instant expiresAt = clock.instant().plus(PAYMENT_WINDOW);
            BigDecimal remaining = enquiry.quote().subtract(enquiry.paid());
            String merchantId = "GKS-" + environment.name() + "-OCC-" + attemptId;
            jdbc.update("""
                    INSERT INTO occasion_payment_attempts(id, enquiry_id, environment, stage, status,
                        amount, merchant_order_id, expires_at)
                    VALUES (?, ?, ?, 'BALANCE', 'PENDING', ?, ?, ?)
                    """, attemptId, id, environment.name(), remaining, merchantId, Timestamp.from(expiresAt));
            return new Start(new Attempt(attemptId, id, environment, "BALANCE", "PENDING",
                    remaining, merchantId, expiresAt, null), true);
        });
        return start.newAttempt() ? startProvider(start.attempt()) : response(start.attempt());
    }

    public Checkout status(ConsentEnvironment environment, UUID subject, UUID enquiryId, UUID attemptId) {
        requireEnabled();
        Attempt attempt = transactions.execute(state -> {
            Enquiry enquiry = loadEnquiry(enquiryId);
            requireOwner(enquiry, environment, subject);
            Attempt own = loadAttempt(attemptId);
            if (!own.enquiryId().equals(enquiryId) || own.environment() != environment)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return own;
        });
        if ("PENDING".equals(attempt.status()) || "EXPIRED".equals(attempt.status())) {
            var result = phonePe.checkStatus(attempt.merchantOrderId());
            switch (result.state().toUpperCase(Locale.ROOT)) {
                case "COMPLETED" -> paid(attempt.merchantOrderId(), result.transactionId());
                case "FAILED" -> failed(attempt.merchantOrderId());
                default -> {
                    if (!attempt.expiresAt().isAfter(clock.instant()) && "DEPOSIT".equals(attempt.stage()))
                        expire(attempt.enquiryId());
                }
            }
        }
        Attempt current = transactions.execute(state -> loadAttempt(attemptId));
        return response(current);
    }

    public Checkout latest(ConsentEnvironment environment, UUID subject, UUID enquiryId) {
        requireEnabled();
        Attempt latest = transactions.execute(state -> {
            requireOwner(loadEnquiry(enquiryId), environment, subject);
            return jdbc.query("""
                    SELECT * FROM occasion_payment_attempts WHERE enquiry_id = ?
                    ORDER BY created_at DESC, id DESC LIMIT 1
                    """, rs -> rs.next() ? mapAttempt(rs) : null, enquiryId);
        });
        if (latest == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return status(environment, subject, enquiryId, latest.id());
    }

    public boolean ownsMerchantOrder(String merchantOrderId) {
        return merchantOrderId != null && merchantOrderId.matches("GKS-(DEV|PROD)-OCC-[0-9a-fA-F-]{36}");
    }

    public void verifiedWebhook(String merchantOrderId, String event, String state, String transactionId) {
        if (!ownsMerchantOrder(merchantOrderId)) throw new IllegalArgumentException("Unknown occasion payment.");
        if ("checkout.order.completed".equals(event) && "COMPLETED".equals(state)) paid(merchantOrderId, transactionId);
        else if ("checkout.order.failed".equals(event) && "FAILED".equals(state)) failed(merchantOrderId);
    }

    private Checkout startProvider(Attempt attempt) {
        if (attempt.url() != null) return response(attempt);
        // An uncertain provider response must remain pending for status reconciliation; never create a second charge.
        if (attempt.expiresAt().isBefore(clock.instant())) throw conflict("The payment window ended. Refresh the status.");
        String storefront = phonePeProperties.getRedirectUrl().replaceAll("/checkout/?$", "");
        String redirect = storefront + "/occasions?enquiry=" + attempt.enquiryId() + "&payment=" + attempt.id();
        var created = phonePe.createPayment(attempt.merchantOrderId(), attempt.amount(), redirect, 600);
        if (created.redirectUrl() == null || created.orderId() == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment provider did not start checkout.");
        transactions.executeWithoutResult(state -> jdbc.update("""
                UPDATE occasion_payment_attempts SET provider_order_id = ?, provider_checkout_url = ?,
                    updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, created.orderId(), created.redirectUrl(), attempt.id()));
        return new Checkout(attempt.id(), attempt.stage(), "PENDING", attempt.amount(),
                attempt.expiresAt(), created.redirectUrl());
    }

    private void paid(String merchantOrderId, String providerTransactionId) {
        try {
            transactions.executeWithoutResult(state -> settle(merchantOrderId, providerTransactionId, true));
        } catch (InventoryConflictException | InventoryNotFoundException failedCommitment) {
            // A verified late success or an inventory conflict requires reconciliation, never false confirmation.
            transactions.executeWithoutResult(state -> refundPending(merchantOrderId));
        }
    }

    private void failed(String merchantOrderId) {
        transactions.executeWithoutResult(state -> settle(merchantOrderId, null, false));
    }

    private void settle(String merchantOrderId, String providerTransactionId, boolean success) {
        UUID id = jdbc.query("SELECT enquiry_id FROM occasion_payment_attempts WHERE merchant_order_id = ?",
                rs -> rs.next() ? (UUID) rs.getObject(1) : null, merchantOrderId);
        if (id == null) throw new IllegalArgumentException("Unknown occasion payment.");
        Enquiry enquiry = lockEnquiry(id);
        Attempt attempt = lockAttempt(merchantOrderId);
        if (success && ("EXPIRED".equals(attempt.status()) || "FAILED".equals(attempt.status()))) {
            refundPending(merchantOrderId);
            return;
        }
        if (!"PENDING".equals(attempt.status())) return;
        if (!success) {
            jdbc.update("UPDATE occasion_payment_attempts SET status = 'FAILED' WHERE id = ?", attempt.id());
            if ("DEPOSIT".equals(attempt.stage())) release(enquiry, "Deposit payment failed");
            return;
        }
        if (!attempt.expiresAt().isAfter(clock.instant())
                || "DEPOSIT".equals(attempt.stage()) && (enquiry.holdExpiry() == null
                        || !enquiry.holdExpiry().isAfter(clock.instant()))
                || "BALANCE".equals(attempt.stage()) && (!"PAID".equals(enquiry.status())
                        || enquiry.balanceDue() != null && !enquiry.balanceDue().isAfter(clock.instant()))) {
            refundPending(merchantOrderId);
            return;
        }
        if ("DEPOSIT".equals(attempt.stage())) {
            for (HeldItem item : heldItems(id)) inventory.confirmHold(item.key());
            BigDecimal paid = enquiry.paid().add(attempt.amount());
            boolean fullyPaid = paid.compareTo(enquiry.quote()) == 0;
            markAttemptPaid(attempt, providerTransactionId);
            if (fullyPaid) orders.create(id, enquiry.environment(), enquiry.subject(), enquiry.branchId(),
                    enquiry.slotId(), enquiry.quote());
            jdbc.update("""
                    UPDATE occasion_enquiries SET status = ?, paid_amount = ?, hold_expires_at = NULL,
                        confirmed_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, fullyPaid ? "CONFIRMED" : "PAID", paid,
                    fullyPaid ? Timestamp.from(clock.instant()) : null, id);
            event(id, "provider", enquiry.status(), fullyPaid ? "CONFIRMED" : "PAID", "Deposit verified");
        } else {
            BigDecimal paid = enquiry.paid().add(attempt.amount());
            if (!"PAID".equals(enquiry.status()) || paid.compareTo(enquiry.quote()) != 0)
                throw conflict("Balance does not match the approved quote.");
            markAttemptPaid(attempt, providerTransactionId);
            orders.create(id, enquiry.environment(), enquiry.subject(), enquiry.branchId(),
                    enquiry.slotId(), enquiry.quote());
            jdbc.update("""
                    UPDATE occasion_enquiries SET status = 'CONFIRMED', paid_amount = ?,
                        confirmed_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, paid, Timestamp.from(clock.instant()), id);
            event(id, "provider", "PAID", "CONFIRMED", "Balance verified");
        }
    }

    private void markAttemptPaid(Attempt attempt, String providerTransactionId) {
        jdbc.update("""
                UPDATE occasion_payment_attempts SET status = 'PAID', provider_transaction_id = ?,
                    paid_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, providerTransactionId, Timestamp.from(clock.instant()), attempt.id());
    }

    private void refundPending(String merchantOrderId) {
        UUID id = jdbc.query("SELECT enquiry_id FROM occasion_payment_attempts WHERE merchant_order_id = ?",
                rs -> rs.next() ? (UUID) rs.getObject(1) : null, merchantOrderId);
        if (id == null) return;
        Enquiry enquiry = lockEnquiry(id);
        Attempt attempt = lockAttempt(merchantOrderId);
        if (!Set.of("PENDING", "EXPIRED", "FAILED").contains(attempt.status())) return;
        jdbc.update("UPDATE occasion_payment_attempts SET status = 'REFUND_PENDING' WHERE id = ?", attempt.id());
        if ("DEPOSIT".equals(attempt.stage()) && ("HELD".equals(enquiry.status())
                || "PAYMENT_PENDING".equals(enquiry.status()))) release(enquiry, "Late payment needs refund review");
        event(id, "provider", enquiry.status(), enquiry.status(), "Verified late payment: manual refund review required");
    }

    public void expire(UUID enquiryId) {
        transactions.executeWithoutResult(state -> {
            Enquiry enquiry = lockEnquiry(enquiryId);
            Attempt pending = pendingAttempt(enquiryId, "DEPOSIT");
            if (!("HELD".equals(enquiry.status()) || "PAYMENT_PENDING".equals(enquiry.status()))
                    || (enquiry.holdExpiry() == null || enquiry.holdExpiry().isAfter(clock.instant()))
                    && (pending == null || pending.expiresAt().isAfter(clock.instant()))) return;
            release(enquiry, "Deposit window expired");
            jdbc.update("""
                    UPDATE occasion_payment_attempts SET status = 'EXPIRED'
                    WHERE enquiry_id = ? AND stage = 'DEPOSIT' AND status = 'PENDING'
                    """, enquiryId);
        });
    }

    /** Batch expiry also releases pickup capacity; inventory expiry alone cannot do that. */
    public void expireDue() {
        List<UUID> due = jdbc.query("""
                SELECT e.id FROM occasion_enquiries e WHERE e.status IN ('HELD', 'PAYMENT_PENDING')
                AND (e.hold_expires_at <= ? OR EXISTS (
                    SELECT 1 FROM occasion_payment_attempts a WHERE a.enquiry_id = e.id
                    AND a.stage = 'DEPOSIT' AND a.status = 'PENDING' AND a.expires_at <= ?))
                ORDER BY e.hold_expires_at LIMIT 100
                """, (rs, row) -> (UUID) rs.getObject(1), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        for (UUID id : due) expire(id);
    }

    private void release(Enquiry enquiry, String reason) {
        for (HeldItem item : heldItems(enquiry.id())) inventory.releaseHold(item.key(), reason);
        if (enquiry.slotId() != null) pickupSlots.releaseNormalCapacity(enquiry.slotId());
        jdbc.update("""
                UPDATE occasion_enquiries SET status = 'EXPIRED', hold_expires_at = NULL,
                    updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, enquiry.id());
        event(enquiry.id(), "system", enquiry.status(), "EXPIRED", reason);
    }

    private List<HeldItem> heldItems(UUID id) {
        return jdbc.query("SELECT reservation_key, product_id FROM occasion_hold_items WHERE enquiry_id = ? ORDER BY product_id",
                (rs, row) -> new HeldItem(rs.getString(1), rs.getLong(2)), id);
    }

    private Attempt pendingAttempt(UUID id, String stage) {
        return jdbc.query("SELECT * FROM occasion_payment_attempts WHERE enquiry_id = ? AND stage = ? AND status = 'PENDING'",
                rs -> rs.next() ? mapAttempt(rs) : null, id, stage);
    }

    private Attempt loadAttempt(UUID id) {
        Attempt result = jdbc.query("SELECT * FROM occasion_payment_attempts WHERE id = ?",
                rs -> rs.next() ? mapAttempt(rs) : null, id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return result;
    }

    private Attempt lockAttempt(String merchantOrderId) {
        Attempt result = jdbc.query("SELECT * FROM occasion_payment_attempts WHERE merchant_order_id = ? FOR UPDATE",
                rs -> rs.next() ? mapAttempt(rs) : null, merchantOrderId);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return result;
    }

    private Attempt mapAttempt(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Attempt((UUID) rs.getObject("id"), (UUID) rs.getObject("enquiry_id"),
                ConsentEnvironment.valueOf(rs.getString("environment")), rs.getString("stage"),
                rs.getString("status"), rs.getBigDecimal("amount"), rs.getString("merchant_order_id"),
                rs.getTimestamp("expires_at").toInstant(), rs.getString("provider_checkout_url"));
    }

    private Enquiry lockEnquiry(UUID id) {
        Enquiry result = jdbc.query("SELECT * FROM occasion_enquiries WHERE id = ? FOR UPDATE",
                rs -> rs.next() ? mapEnquiry(rs) : null, id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return result;
    }

    private Enquiry loadEnquiry(UUID id) {
        Enquiry result = jdbc.query("SELECT * FROM occasion_enquiries WHERE id = ?",
                rs -> rs.next() ? mapEnquiry(rs) : null, id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return result;
    }

    private Enquiry mapEnquiry(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp quoteExpiry = rs.getTimestamp("quote_expires_at");
        Timestamp holdExpiry = rs.getTimestamp("hold_expires_at");
        Timestamp balanceDue = rs.getTimestamp("balance_due_at");
        Long slotId = rs.getObject("pickup_slot_id", Long.class);
        return new Enquiry((UUID) rs.getObject("id"), ConsentEnvironment.valueOf(rs.getString("environment")),
                (UUID) rs.getObject("subject_id"), rs.getLong("branch_id"), rs.getDate("service_date").toLocalDate(),
                rs.getString("fulfilment"), rs.getString("status"), rs.getBigDecimal("quoted_amount"),
                rs.getBigDecimal("deposit_amount"), rs.getBigDecimal("paid_amount"),
                quoteExpiry == null ? null : quoteExpiry.toInstant(),
                holdExpiry == null ? null : holdExpiry.toInstant(),
                balanceDue == null ? null : balanceDue.toInstant(), slotId);
    }

    private Checkout response(Attempt attempt) {
        return new Checkout(attempt.id(), attempt.stage(), attempt.status(), attempt.amount(),
                attempt.expiresAt(), "PENDING".equals(attempt.status()) ? attempt.url() : null);
    }

    private void requireOwner(Enquiry enquiry, ConsentEnvironment environment, UUID subject) {
        if (enquiry.environment() != environment || !enquiry.subject().equals(subject))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private void requireEnabled() {
        if (!features.isOccasionEnquiries() || !features.isOccasionPayments())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private void event(UUID id, String actor, String before, String after, String detail) {
        jdbc.update("INSERT INTO occasion_enquiry_events(enquiry_id, actor, from_status, to_status, detail) VALUES (?, ?, ?, ?, ?)",
                id, actor, before, after, detail);
    }
}
