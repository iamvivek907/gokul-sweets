package com.gokulsweets.restaurant.occasion;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.payment.provider.phonepe.PhonePeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "phonepe.redirect-url=https://ci.example.invalid/checkout")
@Transactional
class OccasionCommitmentIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OccasionCommitmentService commitments;
    @Autowired OccasionEnquiryService enquiries;
    @Autowired OccasionProductionReadinessService readiness;
    @Autowired OccasionCancellationService cancellations;
    @Autowired EnhancementProperties features;
    @Autowired Clock clock;
    @MockitoBean PhonePeClient phonePe;

    @Test
    void managerQuoteSnapshotsConfiguredTaxAndExactItemTotal() {
        var fixture = paidDeposit();
        jdbc.update("DELETE FROM occasion_quote_lines WHERE enquiry_id = ?", fixture.enquiry());
        jdbc.update("UPDATE occasion_enquiries SET status = 'REQUESTED', paid_amount = 0 WHERE id = ?", fixture.enquiry());
        var quoted = enquiries.quote(ConsentEnvironment.DEV, fixture.branch(), fixture.enquiry(), "manager",
                new OccasionEnquiryService.Quote(new BigDecimal("1000.00"), new BigDecimal("200.00"),
                        clock.instant().plus(Duration.ofHours(1)), clock.instant().plus(Duration.ofDays(1)),
                        "Pickup only", java.util.List.of(new OccasionEnquiryService.QuoteLine(fixture.product(),
                        new BigDecimal("1000.00")))));
        assertThat(quoted.pricedLines()).hasSize(1);
        assertThat(quoted.pricedLines().getFirst().subtotal()).isEqualByComparingTo("952.38");
        assertThat(quoted.pricedLines().getFirst().taxAmount()).isEqualByComparingTo("47.62");
    }

    @Test
    void balanceRetryUsesOneCheckoutAndVerifiedPaymentConfirmsOnce() {
        var fixture = paidDeposit();
        boolean enquiries = features.isOccasionEnquiries(), payments = features.isOccasionPayments();
        try {
            features.setOccasionEnquiries(true);
            features.setOccasionPayments(true);
            when(phonePe.createPayment(anyString(), eq(new BigDecimal("800.00")), anyString(), eq(600)))
                    .thenAnswer(invocation -> new PhonePeClient.CreatePaymentResponse("provider-1",
                            invocation.getArgument(0), "PENDING", "https://pay.example/checkout", null, null));
            var first = commitments.beginBalance(ConsentEnvironment.DEV, fixture.subject(), fixture.enquiry());
            var retry = commitments.beginBalance(ConsentEnvironment.DEV, fixture.subject(), fixture.enquiry());
            assertThat(retry.attemptId()).isEqualTo(first.attemptId());
            verify(phonePe, times(1)).createPayment(anyString(), any(), anyString(), eq(600));
            String merchant = merchant(first.attemptId());
            commitments.verifiedWebhook(merchant, "checkout.order.completed", "COMPLETED", "transaction-1");
            commitments.verifiedWebhook(merchant, "checkout.order.completed", "COMPLETED", "transaction-1");
            assertThat(jdbc.queryForObject("SELECT status FROM occasion_enquiries WHERE id = ?", String.class,
                    fixture.enquiry())).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject("SELECT paid_amount FROM occasion_enquiries WHERE id = ?",
                    BigDecimal.class, fixture.enquiry())).isEqualByComparingTo("1000.00");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM occasion_enquiries e JOIN orders o ON o.id = e.order_id
                    JOIN verified_order_ownership own ON own.order_id = o.id
                    WHERE e.id = ? AND o.order_status = 'CONFIRMED' AND own.verified_subject_id = ?
                    """, Integer.class, fixture.enquiry(), fixture.subject())).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT o.order_number FROM occasion_enquiries e JOIN orders o ON o.id = e.order_id
                    WHERE e.id = ?
                    """, String.class, fixture.enquiry())).matches("GKS-[0-9]{8}-[A-F0-9]{16}");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM payments p JOIN occasion_enquiries e ON e.order_id = p.order_id
                    WHERE e.id = ? AND p.payment_status = 'PAID'
                    """, Integer.class, fixture.enquiry())).isEqualTo(2);
        } finally {
            features.setOccasionPayments(payments);
            features.setOccasionEnquiries(enquiries);
        }
    }

    @Test
    void lateSuccessOnExpiredBalanceNeedsRefundAndCannotConfirm() {
        var fixture = paidDeposit();
        UUID attempt = UUID.randomUUID();
        String merchant = "GKS-DEV-OCC-" + attempt;
        jdbc.update("""
                INSERT INTO occasion_payment_attempts(id, enquiry_id, environment, stage, status, amount,
                    merchant_order_id, expires_at)
                VALUES (?, ?, 'DEV', 'BALANCE', 'EXPIRED', 800, ?, ?)
                """, attempt, fixture.enquiry(), merchant,
                Timestamp.from(clock.instant().minus(Duration.ofMinutes(1))));
        commitments.verifiedWebhook(merchant, "checkout.order.completed", "COMPLETED", "late-charge");
        commitments.verifiedWebhook(merchant, "checkout.order.completed", "COMPLETED", "late-charge");
        assertThat(jdbc.queryForObject("SELECT status FROM occasion_payment_attempts WHERE id = ?", String.class,
                attempt)).isEqualTo("REFUND_PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM occasion_enquiries WHERE id = ?", String.class,
                fixture.enquiry())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM occasion_enquiries WHERE id = ?",
                BigDecimal.class, fixture.enquiry())).isEqualByComparingTo("200.00");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void depositRejectsPickupBeforeReadinessWithoutCapacityOrPaymentSideEffects() {
        var fixture = paidDeposit();
        boolean oldEnquiries = features.isOccasionEnquiries(), oldPayments = features.isOccasionPayments();
        try {
            features.setOccasionEnquiries(true);
            features.setOccasionPayments(true);
            Long slot = jdbc.queryForObject("SELECT pickup_slot_id FROM occasion_enquiries WHERE id = ?",
                    Long.class, fixture.enquiry());
            var date = jdbc.queryForObject("SELECT service_date FROM occasion_enquiries WHERE id = ?",
                    Date.class, fixture.enquiry());
            jdbc.update("UPDATE occasion_enquiries SET status = 'QUOTED', paid_amount = 0 WHERE id = ?", fixture.enquiry());
            jdbc.update("DELETE FROM occasion_payment_attempts WHERE enquiry_id = ?", fixture.enquiry());
            jdbc.update("UPDATE pickup_slots SET booked_count = 0 WHERE id = ?", slot);
            Long bp = jdbc.queryForObject("INSERT INTO branch_products(branch_id, product_id) VALUES (?, ?) RETURNING id",
                    Long.class, fixture.branch(), fixture.product());
            jdbc.update("""
                    INSERT INTO branch_inventory_policies(branch_product_id, control_mode, inventory_unit,
                        online_enabled, booking_horizon_days, created_at, updated_at)
                    VALUES (?, 'DAILY_PRODUCTION', 'PIECE', true, 30, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, bp);
            jdbc.update("""
                    INSERT INTO inventory_daily_allocations(branch_product_id, service_date, status,
                        inventory_unit, approved_quantity, expected_ready_at, created_at, updated_at)
                    VALUES (?, ?, 'APPROVED', 'PIECE', 100, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, bp, date, Timestamp.valueOf(date.toLocalDate().atTime(12, 1)));
            assertThatThrownBy(() -> commitments.beginDeposit(ConsentEnvironment.DEV,
                    fixture.subject(), fixture.enquiry(), slot)).hasMessageContaining("ready later");
            assertThat(jdbc.queryForObject("SELECT booked_count FROM pickup_slots WHERE id = ?",
                    Integer.class, slot)).isZero();
            assertThat(jdbc.queryForObject("SELECT held_quantity FROM inventory_daily_allocations WHERE branch_product_id = ?",
                    BigDecimal.class, bp)).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM occasion_payment_attempts WHERE enquiry_id = ?",
                    Integer.class, fixture.enquiry())).isZero();
            verifyNoInteractions(phonePe);

            // Equality is valid: kitchen readiness and pickup start are both interpreted in IST.
            jdbc.update("UPDATE inventory_daily_allocations SET expected_ready_at = ? WHERE branch_product_id = ?",
                    Timestamp.valueOf(date.toLocalDate().atTime(12, 0)), bp);
            when(phonePe.createPayment(anyString(), eq(new BigDecimal("200.00")), anyString(), eq(600)))
                    .thenAnswer(invocation -> new PhonePeClient.CreatePaymentResponse("deposit-ready",
                            invocation.getArgument(0), "PENDING", "https://pay.example/checkout", null, null));
            var checkout = commitments.beginDeposit(ConsentEnvironment.DEV,
                    fixture.subject(), fixture.enquiry(), slot);
            assertThat(checkout.status()).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT booked_count FROM pickup_slots WHERE id = ?",
                    Integer.class, slot)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT held_quantity FROM inventory_daily_allocations WHERE branch_product_id = ?",
                    BigDecimal.class, bp)).isEqualByComparingTo("10");
            verify(phonePe).createPayment(anyString(), any(), anyString(), eq(600));
        } finally {
            features.setOccasionEnquiries(oldEnquiries);
            features.setOccasionPayments(oldPayments);
        }
    }

    @Test
    void bulkApprovalCreates100KgPlanAndPaymentCommitsWithoutDailyInventory() {
        var fixture = paidDeposit();
        boolean oldBulk = features.isOccasionBulkProduction();
        boolean oldEnquiries = features.isOccasionEnquiries(), oldPayments = features.isOccasionPayments();
        try {
            features.setOccasionBulkProduction(true);
            features.setOccasionEnquiries(true);
            features.setOccasionPayments(true);
            jdbc.update("DELETE FROM occasion_payment_attempts WHERE enquiry_id = ?", fixture.enquiry());
            jdbc.update("UPDATE occasion_enquiries SET status = 'REQUESTED', paid_amount = 0 WHERE id = ?", fixture.enquiry());
            jdbc.update("UPDATE products SET sale_mode = 'WEIGHT' WHERE id = ?", fixture.product());
            jdbc.update("UPDATE occasion_enquiry_items SET requested_quantity = 100000, unit = 'GRAM' WHERE enquiry_id = ?", fixture.enquiry());
            var date = jdbc.queryForObject("SELECT service_date FROM occasion_enquiries WHERE id = ?", Date.class, fixture.enquiry()).toLocalDate();
            Long slot = jdbc.queryForObject("SELECT pickup_slot_id FROM occasion_enquiries WHERE id = ?", Long.class, fixture.enquiry());
            jdbc.update("UPDATE pickup_slots SET booked_count = 0 WHERE id = ?", slot);
            var quote = new OccasionEnquiryService.Quote(new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                    clock.instant().plus(Duration.ofHours(1)), null, "Dedicated production after procurement review",
                    java.util.List.of(new OccasionEnquiryService.QuoteLine(fixture.product(), new BigDecimal("1000.00"))),
                    date.atTime(12, 0));
            var approved = enquiries.quote(ConsentEnvironment.DEV, fixture.branch(), fixture.enquiry(), "manager", quote);
            assertThat(approved.productionPlan()).hasSize(1);
            assertThat(approved.productionPlan().getFirst().quantity()).isEqualByComparingTo("100000");
            assertThat(approved.productionPlan().getFirst().state()).isEqualTo("PLANNED");
            // Reapproval replaces, rather than duplicates, the uncommitted allocation.
            enquiries.quote(ConsentEnvironment.DEV, fixture.branch(), fixture.enquiry(), "manager", quote);
            Integer dailyBefore = jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations", Integer.class);
            when(phonePe.createPayment(anyString(), eq(new BigDecimal("1000.00")), anyString(), eq(600)))
                    .thenAnswer(invocation -> new PhonePeClient.CreatePaymentResponse("bulk-provider",
                            invocation.getArgument(0), "PENDING", "https://pay.example/bulk", null, null));
            var checkout = commitments.beginDeposit(ConsentEnvironment.DEV, fixture.subject(), fixture.enquiry(), slot);
            assertThat(jdbc.queryForObject("SELECT state FROM occasion_production_allocations WHERE enquiry_id = ?", String.class, fixture.enquiry())).isEqualTo("HELD");
            commitments.verifiedWebhook(merchant(checkout.attemptId()), "checkout.order.completed", "COMPLETED", "bulk-paid");
            commitments.verifiedWebhook(merchant(checkout.attemptId()), "checkout.order.completed", "COMPLETED", "bulk-paid");
            assertThat(jdbc.queryForObject("SELECT state FROM occasion_production_allocations WHERE enquiry_id = ?", String.class, fixture.enquiry())).isEqualTo("COMMITTED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_daily_allocations", Integer.class)).isEqualTo(dailyBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM occasion_hold_items WHERE enquiry_id = ?", Integer.class, fixture.enquiry())).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM occasion_enquiries WHERE id = ?", String.class, fixture.enquiry())).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject("SELECT weight_grams FROM order_items WHERE order_id = (SELECT order_id FROM occasion_enquiries WHERE id = ?)", Integer.class, fixture.enquiry())).isEqualTo(100000);
            Long orderId = jdbc.queryForObject("SELECT order_id FROM occasion_enquiries WHERE id = ?", Long.class, fixture.enquiry());
            assertThatThrownBy(() -> readiness.requireReady(orderId)).hasMessageContaining("full actual ready quantity");
            jdbc.update("UPDATE orders SET order_status = 'PREPARING' WHERE id = ?", orderId);
            assertThatThrownBy(() -> readiness.record(ConsentEnvironment.DEV, fixture.branch() + 1,
                    fixture.enquiry(), fixture.product(), new BigDecimal("60000"), 0, "staff"))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThatThrownBy(() -> readiness.record(ConsentEnvironment.DEV, fixture.branch(),
                    fixture.enquiry(), fixture.product(), new BigDecimal("100001"), 0, "staff"))
                    .hasMessageContaining("approved quantity");
            readiness.record(ConsentEnvironment.DEV, fixture.branch(), fixture.enquiry(), fixture.product(), new BigDecimal("60000"), 0, "staff-a");
            assertThatThrownBy(() -> readiness.requireReady(orderId)).hasMessageContaining("full actual ready quantity");
            assertThatThrownBy(() -> readiness.record(ConsentEnvironment.DEV, fixture.branch(),
                    fixture.enquiry(), fixture.product(), new BigDecimal("100000"), 0, "staff-b"))
                    .hasMessageContaining("Another staff member");
            readiness.record(ConsentEnvironment.DEV, fixture.branch(), fixture.enquiry(), fixture.product(), new BigDecimal("100000"), 1, "staff-b");
            readiness.requireReady(orderId);
            assertThatThrownBy(() -> cancellations.cancel(ConsentEnvironment.DEV, fixture.branch(),
                    fixture.enquiry(), "manager", "Cannot cancel prepared food here"))
                    .hasMessageContaining("Preparation has started");
            readiness.record(ConsentEnvironment.DEV, fixture.branch(), fixture.enquiry(), fixture.product(), new BigDecimal("100000"), 2, "staff-b");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM occasion_production_readiness_events WHERE enquiry_id = ?", Integer.class, fixture.enquiry())).isEqualTo(2);
            jdbc.update("UPDATE orders SET order_status = 'READY_FOR_PICKUP' WHERE id = ?", orderId);
            assertThatThrownBy(() -> readiness.record(ConsentEnvironment.DEV, fixture.branch(),
                    fixture.enquiry(), fixture.product(), new BigDecimal("50000"), 2, "staff-b"))
                    .hasMessageContaining("while this confirmed order is preparing");
        } finally {
            features.setOccasionBulkProduction(oldBulk);
            features.setOccasionEnquiries(oldEnquiries);
            features.setOccasionPayments(oldPayments);
        }
    }

    @Test
    void failedBulkDepositReleasesDedicatedPlanAndSlot() {
        var fixture = paidDeposit();
        boolean oldBulk = features.isOccasionBulkProduction();
        boolean oldEnquiries = features.isOccasionEnquiries(), oldPayments = features.isOccasionPayments();
        try {
            features.setOccasionBulkProduction(true);
            features.setOccasionEnquiries(true);
            features.setOccasionPayments(true);
            jdbc.update("DELETE FROM occasion_payment_attempts WHERE enquiry_id = ?", fixture.enquiry());
            jdbc.update("UPDATE occasion_enquiries SET status = 'QUOTED', paid_amount = 0 WHERE id = ?", fixture.enquiry());
            Long slot = jdbc.queryForObject("SELECT pickup_slot_id FROM occasion_enquiries WHERE id = ?", Long.class, fixture.enquiry());
            jdbc.update("UPDATE pickup_slots SET booked_count = 0 WHERE id = ?", slot);
            Date date = jdbc.queryForObject("SELECT service_date FROM occasion_enquiries WHERE id = ?", Date.class, fixture.enquiry());
            jdbc.update("""
                    INSERT INTO occasion_production_allocations(enquiry_id, product_id, quantity, unit, expected_ready_at, state, approved_by)
                    VALUES (?, ?, 10, 'PIECE', ?, 'PLANNED', 'manager')
                    """, fixture.enquiry(), fixture.product(), Timestamp.valueOf(date.toLocalDate().atTime(11, 0)));
            when(phonePe.createPayment(anyString(), eq(new BigDecimal("200.00")), anyString(), eq(600)))
                    .thenAnswer(invocation -> new PhonePeClient.CreatePaymentResponse("bulk-failure",
                            invocation.getArgument(0), "PENDING", "https://pay.example/bulk", null, null));
            var checkout = commitments.beginDeposit(ConsentEnvironment.DEV, fixture.subject(), fixture.enquiry(), slot);
            commitments.verifiedWebhook(merchant(checkout.attemptId()), "checkout.order.failed", "FAILED", null);
            commitments.verifiedWebhook(merchant(checkout.attemptId()), "checkout.order.failed", "FAILED", null);
            assertThat(jdbc.queryForObject("SELECT state FROM occasion_production_allocations WHERE enquiry_id = ?", String.class, fixture.enquiry())).isEqualTo("RELEASED");
            assertThat(jdbc.queryForObject("SELECT booked_count FROM pickup_slots WHERE id = ?", Integer.class, slot)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM occasion_hold_items WHERE enquiry_id = ?", Integer.class, fixture.enquiry())).isZero();
        } finally {
            features.setOccasionBulkProduction(oldBulk);
            features.setOccasionEnquiries(oldEnquiries);
            features.setOccasionPayments(oldPayments);
        }
    }

    @Test
    void paidBulkCancellationReleasesOnceAndLateBalanceNeedsRefundReview() {
        var fixture = paidDeposit();
        boolean oldBulk = features.isOccasionBulkProduction();
        boolean oldEnquiries = features.isOccasionEnquiries(), oldPayments = features.isOccasionPayments();
        try {
            features.setOccasionBulkProduction(true);
            features.setOccasionEnquiries(true);
            features.setOccasionPayments(true);
            Date date = jdbc.queryForObject("SELECT service_date FROM occasion_enquiries WHERE id = ?", Date.class, fixture.enquiry());
            Long slot = jdbc.queryForObject("SELECT pickup_slot_id FROM occasion_enquiries WHERE id = ?", Long.class, fixture.enquiry());
            jdbc.update("""
                    INSERT INTO occasion_production_allocations(enquiry_id, product_id, quantity, unit, expected_ready_at, state, approved_by)
                    VALUES (?, ?, 10, 'PIECE', ?, 'COMMITTED', 'manager')
                    """, fixture.enquiry(), fixture.product(), Timestamp.valueOf(date.toLocalDate().atTime(11, 0)));
            when(phonePe.createPayment(anyString(), eq(new BigDecimal("800.00")), anyString(), eq(600)))
                    .thenAnswer(invocation -> new PhonePeClient.CreatePaymentResponse("cancel-balance",
                            invocation.getArgument(0), "PENDING", "https://pay.example/balance", null, null));
            var checkout = commitments.beginBalance(ConsentEnvironment.DEV, fixture.subject(), fixture.enquiry());
            cancellations.cancel(ConsentEnvironment.DEV, fixture.branch(), fixture.enquiry(), "manager", "Customer cannot attend; finance to review terms");
            cancellations.cancel(ConsentEnvironment.DEV, fixture.branch(), fixture.enquiry(), "manager", "Repeated request");
            assertThat(jdbc.queryForObject("SELECT booked_count FROM pickup_slots WHERE id = ?", Integer.class, slot)).isZero();
            assertThat(jdbc.queryForObject("SELECT state FROM occasion_production_allocations WHERE enquiry_id = ?", String.class, fixture.enquiry())).isEqualTo("RELEASED");
            assertThat(jdbc.queryForObject("SELECT paid_amount FROM occasion_cancellation_reviews WHERE enquiry_id = ?", BigDecimal.class, fixture.enquiry())).isEqualByComparingTo("200.00");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM occasion_cancellation_reviews WHERE enquiry_id = ?", Integer.class, fixture.enquiry())).isEqualTo(1);
            commitments.verifiedWebhook(merchant(checkout.attemptId()), "checkout.order.completed", "COMPLETED", "late-cancelled-balance");
            assertThat(jdbc.queryForObject("SELECT status FROM occasion_payment_attempts WHERE id = ?", String.class, checkout.attemptId())).isEqualTo("REFUND_PENDING");
            assertThat(jdbc.queryForObject("SELECT status FROM occasion_enquiries WHERE id = ?", String.class, fixture.enquiry())).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT paid_amount FROM occasion_enquiries WHERE id = ?", BigDecimal.class, fixture.enquiry())).isEqualByComparingTo("200.00");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM occasion_payment_attempts WHERE enquiry_id = ? AND status = 'PAID'", Integer.class, fixture.enquiry())).isEqualTo(1);
        } finally {
            features.setOccasionBulkProduction(oldBulk);
            features.setOccasionEnquiries(oldEnquiries);
            features.setOccasionPayments(oldPayments);
        }
    }

    private String merchant(UUID attemptId) {
        return jdbc.queryForObject("SELECT merchant_order_id FROM occasion_payment_attempts WHERE id = ?",
                String.class, attemptId);
    }

    private Fixture paidDeposit() {
        UUID suffix = UUID.randomUUID();
        long branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Occasion test') RETURNING id",
                Long.class, "OCC-" + suffix.toString().substring(0, 8));
        long category = jdbc.queryForObject("INSERT INTO categories(code, name) VALUES (?, 'Occasion category') RETURNING id",
                Long.class, "OCC-C-" + suffix.toString().substring(0, 8));
        long tax = jdbc.queryForObject("""
                INSERT INTO tax_categories(code, name, cgst_rate, sgst_rate)
                VALUES (?, 'Occasion food', 2.5, 2.5) RETURNING id
                """, Long.class, "OCC-T-" + suffix.toString().substring(0, 8));
        long product = jdbc.queryForObject("""
                INSERT INTO products(code, category_id, name, base_price, tax_category_id)
                VALUES (?, ?, 'Occasion sweets', 100, ?)
                RETURNING id
                """, Long.class, "OCC-P-" + suffix.toString().substring(0, 8), category, tax);
        var date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(2);
        long slot = jdbc.queryForObject("""
                INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time, capacity, booked_count)
                VALUES (?, ?, '12:00', '12:30', 10, 1) RETURNING id
                """, Long.class, branch, Date.valueOf(date));
        UUID subject = UUID.randomUUID(), enquiry = UUID.randomUUID();
        jdbc.update("INSERT INTO verified_customer_subjects(id, environment, verified_phone) VALUES (?, 'DEV', ?)",
                subject, "+91" + (7000000000L + Math.abs(suffix.hashCode())));
        jdbc.update("""
                INSERT INTO occasion_enquiries(id, environment, subject_id, branch_id, occasion_type,
                    service_date, guest_count, fulfilment, status, quoted_amount, deposit_amount,
                    paid_amount, quote_terms, quote_expires_at, balance_due_at, pickup_slot_id)
                VALUES (?, 'DEV', ?, ?, 'Celebration', ?, 20, 'PICKUP', 'PAID', 1000, 200,
                    200, 'Pickup only', ?, ?, ?)
                """, enquiry, subject, branch,
                Date.valueOf(date),
                Timestamp.from(clock.instant().plus(Duration.ofHours(1))),
                Timestamp.from(clock.instant().plus(Duration.ofDays(1))), slot);
        jdbc.update("""
                INSERT INTO occasion_quote_lines(enquiry_id, product_id, product_name, sale_mode,
                    quantity, gross_amount, subtotal, tax_amount, cgst_rate, sgst_rate)
                VALUES (?, ?, 'Occasion sweets', 'UNIT', 10, 1000, 952.38, 47.62, 2.5, 2.5)
                """, enquiry, product);
        jdbc.update("""
                INSERT INTO occasion_enquiry_items(enquiry_id, product_id, requested_quantity, unit)
                VALUES (?, ?, 10, 'PIECE')
                """, enquiry, product);
        UUID deposit = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO occasion_payment_attempts(id, enquiry_id, environment, stage, status,
                    amount, merchant_order_id, expires_at, paid_at)
                VALUES (?, ?, 'DEV', 'DEPOSIT', 'PAID', 200, ?, ?, ?)
                """, deposit, enquiry, "GKS-DEV-OCC-" + deposit,
                Timestamp.from(clock.instant().minus(Duration.ofMinutes(1))), Timestamp.from(clock.instant()));
        return new Fixture(enquiry, subject, branch, product);
    }

    private record Fixture(UUID enquiry, UUID subject, long branch, long product) {}
}
