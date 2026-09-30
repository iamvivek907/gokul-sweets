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

// Legacy fixture scenarios deliberately retain the original stock path; bulk tests enable their gate explicitly.
@SpringBootTest(properties = {"phonepe.redirect-url=https://ci.example.invalid/checkout",
        "gokul.features.occasion-bulk-production=false"})
@Transactional
class OccasionCommitmentIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OccasionCommitmentService commitments;
    @Autowired OccasionEnquiryService enquiries;
    @Autowired OccasionOrderFinalizer orders;
    @Autowired OccasionProductionReadinessService readiness;
    @Autowired OccasionCancellationService cancellations;
    @Autowired EnhancementProperties features;
    @Autowired Clock clock;
    @Autowired OccasionQuoteCalculator calculator;
    @Autowired OccasionPackingFinalizer packing;
    @Autowired com.gokulsweets.restaurant.reporting.AnalyticsRefreshService analytics;
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

    @Test
    void fullyPaidCancellationCancelsLinkedOrderAndPreservesVerifiedPayments() {
        var fixture = paidDeposit();
        boolean oldBulk = features.isOccasionBulkProduction();
        boolean oldEnquiries = features.isOccasionEnquiries(), oldPayments = features.isOccasionPayments();
        try {
            features.setOccasionBulkProduction(true);
            features.setOccasionEnquiries(true);
            features.setOccasionPayments(true);
            Date date = jdbc.queryForObject("SELECT service_date FROM occasion_enquiries WHERE id = ?", Date.class, fixture.enquiry());
            jdbc.update("""
                    INSERT INTO occasion_production_allocations(enquiry_id, product_id, quantity, unit, expected_ready_at, state, approved_by)
                    VALUES (?, ?, 10, 'PIECE', ?, 'COMMITTED', 'manager')
                    """, fixture.enquiry(), fixture.product(), Timestamp.valueOf(date.toLocalDate().atTime(11, 0)));
            when(phonePe.createPayment(anyString(), eq(new BigDecimal("800.00")), anyString(), eq(600)))
                    .thenAnswer(invocation -> new PhonePeClient.CreatePaymentResponse("cancel-full",
                            invocation.getArgument(0), "PENDING", "https://pay.example/balance", null, null));
            var checkout = commitments.beginBalance(ConsentEnvironment.DEV, fixture.subject(), fixture.enquiry());
            commitments.verifiedWebhook(merchant(checkout.attemptId()), "checkout.order.completed", "COMPLETED", "full-balance");
            assertThatThrownBy(() -> cancellations.cancel(ConsentEnvironment.PROD, fixture.branch(),
                    fixture.enquiry(), "manager", "Wrong environment"))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            cancellations.cancel(ConsentEnvironment.DEV, fixture.branch(), fixture.enquiry(), "manager", "Customer requested cancellation before preparation");
            assertThat(jdbc.queryForObject("SELECT order_status FROM orders WHERE id = (SELECT order_id FROM occasion_enquiries WHERE id = ?)", String.class, fixture.enquiry())).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT paid_amount FROM occasion_cancellation_reviews WHERE enquiry_id = ?", BigDecimal.class, fixture.enquiry())).isEqualByComparingTo("1000.00");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = (SELECT order_id FROM occasion_enquiries WHERE id = ?) AND payment_status = 'PAID'", Integer.class, fixture.enquiry())).isEqualTo(2);
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

    @Autowired OccasionCatalogue catalogue;
    @Autowired com.gokulsweets.restaurant.branchproduct.BranchProductRepository branchProducts;

    @Test
    void pieceRequestRequiresSizingAndPreservesRequestedPiecesWhilePlanningWeight() {
        var f=paidDeposit();boolean old=features.isOccasionBulkProduction();
        try {
            features.setOccasionBulkProduction(true);
            jdbc.update("INSERT INTO branch_products(branch_id,product_id,occasion_only) VALUES(?,?,true)",f.branch(),f.product());
            jdbc.update("UPDATE products SET sale_mode='WEIGHT',minimum_weight_grams=250,weight_step_grams=50 WHERE id=?",f.product());
            jdbc.update("UPDATE occasion_enquiries SET status='REQUESTED',paid_amount=0 WHERE id=?",f.enquiry());
            jdbc.update("UPDATE occasion_enquiry_items SET requested_quantity=1000 WHERE enquiry_id=?",f.enquiry());
            var date=jdbc.queryForObject("SELECT service_date FROM occasion_enquiries WHERE id=?",java.sql.Date.class,f.enquiry()).toLocalDate();
            var expiry=clock.instant().plus(Duration.ofHours(1));var balance=clock.instant().plus(Duration.ofDays(1));
            assertThatThrownBy(()->enquiries.quote(ConsentEnvironment.DEV,f.branch(),f.enquiry(),"manager",
                new OccasionEnquiryService.Quote(new BigDecimal("1000"),new BigDecimal("200"),expiry,balance,"1000 measured pieces",java.util.List.of(new OccasionEnquiryService.QuoteLine(f.product(),new BigDecimal("1000"))),date.atTime(10,0))))
                .hasMessageContaining("Approve a whole-gram");
            var approved=enquiries.quote(ConsentEnvironment.DEV,f.branch(),f.enquiry(),"manager",
                new OccasionEnquiryService.Quote(new BigDecimal("1000"),new BigDecimal("200"),expiry,balance,"1000 measured pieces",java.util.List.of(new OccasionEnquiryService.QuoteLine(f.product(),new BigDecimal("1000"),new BigDecimal("20000"))),date.atTime(10,0)));
            assertThat(approved.items().getFirst().quantity()).isEqualByComparingTo("1000");
            assertThat(approved.items().getFirst().unit()).isEqualTo(OccasionEnquiryService.Unit.PIECE);
            assertThat(approved.productionPlan().getFirst().quantity()).isEqualByComparingTo("20000");
            assertThat(approved.productionPlan().getFirst().unit()).isEqualTo("GRAM");
            assertThat(jdbc.queryForObject("SELECT weight_grams FROM occasion_quote_lines WHERE enquiry_id=?",Integer.class,f.enquiry())).isEqualTo(20000);
            assertThat(branchProducts.findForOrder(f.branch(),java.util.List.of(f.product()))).isEmpty();
            assertThat(branchProducts.findAvailableMenu(f.branch())).isEmpty();
            jdbc.update("UPDATE branch_products SET available=false WHERE branch_id=? AND product_id=?",f.branch(),f.product());
            assertThat(catalogue.catalogue(f.branch(),false).sweets()).anyMatch(sweet->sweet.id()==f.product());
        } finally {features.setOccasionBulkProduction(old);}
    }

    @Test
    void giftArithmeticAndSnapshotRemainStableAfterCatalogueChanges() {
        var f=paidDeposit();boolean old=features.isOccasionEnquiries();
        try {
            features.setOccasionEnquiries(true);
            var box=catalogue.saveBox(f.branch(),new OccasionCatalogue.Box(null,"Celebration eight","https://images.example.invalid/real-box.jpg","18 × 12 × 4 cm","Food-safe cardboard",3,8,new BigDecimal("10.00"),"Ribbon",1,true));
            var recipe=java.util.List.of(new OccasionCatalogue.Recipe(f.product(),4),new OccasionCatalogue.Recipe(f.product()+1,2),new OccasionCatalogue.Recipe(f.product()+2,2));
            var items=java.util.List.of(new OccasionEnquiryService.Item(f.product(),new BigDecimal("2800"),OccasionEnquiryService.Unit.PIECE,"Barfi"),new OccasionEnquiryService.Item(f.product()+1,new BigDecimal("1400"),OccasionEnquiryService.Unit.PIECE,"Peda"),new OccasionEnquiryService.Item(f.product()+2,new BigDecimal("1400"),OccasionEnquiryService.Unit.PIECE,"Laddoo"));
            var date=LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(3);
            var snapshot=catalogue.validateGift(f.branch(),date,items,new OccasionCatalogue.GiftRequest(box.id(),700,recipe));
            assertThat(snapshot.packagingEstimate()).isEqualByComparingTo("7000");
            assertThat(snapshot.boxCount()).isEqualTo(700);
            assertThatThrownBy(()->catalogue.validateGift(f.branch(),date,items,new OccasionCatalogue.GiftRequest(box.id(),701,recipe))).hasMessageContaining("multiplied");
            assertThatThrownBy(()->catalogue.validateGift(f.branch(),date,items,new OccasionCatalogue.GiftRequest(box.id(),700,java.util.List.of(new OccasionCatalogue.Recipe(f.product(),9))))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            jdbc.update("UPDATE occasion_enquiries SET packaging_snapshot=?::jsonb WHERE id=?",new tools.jackson.databind.ObjectMapper().writeValueAsString(snapshot),f.enquiry());
            catalogue.saveBox(f.branch(),new OccasionCatalogue.Box(box.id(),"Changed box",null,"20 cm","Paper",1,4,null,"",0,false));
            assertThat(enquiries.staffGet(ConsentEnvironment.DEV,f.branch(),f.enquiry()).gift().box().name()).isEqualTo("Celebration eight");
            assertThatThrownBy(()->catalogue.validateGift(f.branch(),date,items,new OccasionCatalogue.GiftRequest(box.id(),700,recipe))).hasMessageContaining("unavailable");
        }finally{features.setOccasionEnquiries(old);}
    }

    @Test
    void categoryGalleryAndBrandingAreBranchScopedAndPublicationSafe() {
        var f=paidDeposit();boolean old=features.isOccasionEnquiries();
        try {
            features.setOccasionEnquiries(true);
            jdbc.update("INSERT INTO branch_products(branch_id,product_id) VALUES(?,?)",f.branch(),f.product());
            var photos=java.util.List.of("https://images.example.invalid/outer.jpg","https://images.example.invalid/inside.jpg");
            var box=catalogue.saveBox(f.branch(),new OccasionCatalogue.Box(null,"Gallery box",photos.getFirst(),"20 cm","Food-safe card",2,8,null,"",1,true,photos));
            catalogue.saveBranding(f.branch(),new OccasionCatalogue.Branding("Celebrate with Gokul","Made for your gathering",photos.getFirst(),false));
            assertThat(catalogue.catalogue(f.branch(),false).branding()).isNull();
            assertThat(catalogue.catalogue(f.branch(),true).branding().headline()).isEqualTo("Celebrate with Gokul");
            catalogue.saveBranding(f.branch(),new OccasionCatalogue.Branding("Celebrate with Gokul","Made for your gathering",photos.getFirst(),true));
            var live=catalogue.catalogue(f.branch(),false);
            assertThat(live.sweets().getFirst().categoryName()).isEqualTo("Occasion category");
            assertThat(live.boxes().getFirst().imageUrls()).containsExactlyElementsOf(photos);
            assertThat(live.branding().published()).isTrue();
            assertThatThrownBy(()->catalogue.saveBox(f.branch()+100000,new OccasionCatalogue.Box(box.id(),"Other branch",null,"20 cm","Card",1,8,null,"",1,true,photos)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThatThrownBy(()->catalogue.saveBranding(f.branch(),new OccasionCatalogue.Branding("Invalid","Invalid","http://unsafe.example",true)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }finally{features.setOccasionEnquiries(old);}
    }

    @Test
    void automaticQuotePricesFoodBoxesAndSpoonsAndFinalPackingUsesFrozenRates() {
        boolean bulk=features.isOccasionBulkProduction(), enabled=features.isOccasionEnquiries();
        try {
            features.setOccasionBulkProduction(true);features.setOccasionEnquiries(true);
            var f=paidDeposit();
            jdbc.update("UPDATE products SET sale_mode='WEIGHT' WHERE id=?",f.product());
            jdbc.update("INSERT INTO branch_products(branch_id,product_id,price_override,occasion_piece_grams) VALUES(?,?,400,20)",f.branch(),f.product());
            jdbc.update("UPDATE occasion_enquiry_items SET requested_quantity=600 WHERE enquiry_id=?",f.enquiry());
            var box=catalogue.saveBox(f.branch(),new OccasionCatalogue.Box(null,"400 ml plastic box",null,"400 ml","Food-safe plastic",1,3,new BigDecimal("10"),"",0,true));
            var snapshot=new OccasionCatalogue.GiftSnapshot(box,600,java.util.List.of(new OccasionCatalogue.Recipe(f.product(),1)),new BigDecimal("6000"),null,true);
            jdbc.update("UPDATE occasion_enquiries SET status='REQUESTED',paid_amount=0,packaging_snapshot=?::jsonb WHERE id=?",new tools.jackson.databind.ObjectMapper().writeValueAsString(snapshot),f.enquiry());
            var rates=java.util.List.of(new OccasionQuoteCalculator.Rate(f.product(),null,null,new BigDecimal("12")));
            var extras=java.util.List.of(new OccasionQuoteCalculator.Extra("Plastic spoons",600,new BigDecimal("1")));
            var input=new OccasionQuoteCalculator.Input(rates,new BigDecimal("25"),null,true,java.time.LocalTime.of(10,0),"600 pieces; actual packed weights at agreed rates",true,extras,null);
            var preview=calculator.preview(ConsentEnvironment.DEV,f.branch(),f.enquiry(),input);
            assertThat(preview.foodBase()).isEqualByComparingTo("4800");
            assertThat(preview.foodTax()).isEqualByComparingTo("240");
            assertThat(preview.packagingTotal()).isEqualByComparingTo("6600");
            assertThat(preview.total()).isEqualByComparingTo("11640");
            assertThat(preview.deposit()).isEqualByComparingTo("2910");
            assertThat(preview.lines().getFirst().productionQuantity()).isEqualByComparingTo("12000");
            assertThatThrownBy(()->calculator.approve(ConsentEnvironment.DEV,f.branch(),f.enquiry(),"manager",input)).hasMessageContaining("review");
            var approved=calculator.approve(ConsentEnvironment.DEV,f.branch(),f.enquiry(),"manager",
                new OccasionQuoteCalculator.Input(rates,new BigDecimal("25"),null,true,java.time.LocalTime.of(10,0),input.terms(),true,extras,preview.total()));
            assertThat(approved.estimated()).isTrue();assertThat(approved.gift().approvedPackagingTotal()).isEqualByComparingTo("6000");
            assertThat(approved.extraCharges().getFirst().quantity()).isEqualTo(600);
            assertThatThrownBy(()->commitments.beginDeposit(ConsentEnvironment.DEV,f.subject(),f.enquiry(),approved.pickupSlotId())).hasMessageContaining("Accept");
            jdbc.update("UPDATE occasion_enquiries SET status='PAID',paid_amount=deposit_amount WHERE id=?",f.enquiry());
            jdbc.update("UPDATE occasion_production_allocations SET state='COMMITTED' WHERE enquiry_id=?",f.enquiry());
            var actual=new OccasionPackingFinalizer.Input(java.util.List.of(new OccasionPackingFinalizer.Packed(f.product(),new BigDecimal("12500"))),0,true);
            assertThatThrownBy(()->packing.finalizePacking(ConsentEnvironment.DEV,f.branch(),f.enquiry(),"manager",actual)).hasMessageContaining("pickup date");
            assertThatThrownBy(()->commitments.beginBalance(ConsentEnvironment.DEV,f.subject(),f.enquiry())).hasMessageContaining("finalize");
            var today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
            jdbc.update("UPDATE occasion_enquiries SET service_date=? WHERE id=?",Date.valueOf(today),f.enquiry());
            jdbc.update("UPDATE pickup_slots SET slot_date=?,start_time='23:00',end_time='23:59:59' WHERE id=?",Date.valueOf(today),approved.pickupSlotId());
            jdbc.update("UPDATE branch_products SET price_override=999 WHERE branch_id=? AND product_id=?",f.branch(),f.product());
            jdbc.update("UPDATE tax_categories SET cgst_rate=9,sgst_rate=9 WHERE id=(SELECT tax_category_id FROM products WHERE id=?)",f.product());
            var finalized=packing.finalizePacking(ConsentEnvironment.DEV,f.branch(),f.enquiry(),"manager",actual);
            assertThat(finalized.quotedAmount()).isEqualByComparingTo("11850");
            assertThat(finalized.originalEstimate()).isEqualByComparingTo("11640");
            assertThat(finalized.calculation().lines().getFirst().unitPrice()).isEqualByComparingTo("400");
            assertThat(finalized.calculation().foodTax()).isEqualByComparingTo("250");
            assertThat(finalized.balancePaymentOpen()).isTrue();assertThat(finalized.packingFinalizedAt()).isNotNull();
            assertThat(finalized.items().getFirst().quantity()).isEqualByComparingTo("600");
            assertThat(finalized.productionPlan().getFirst().readyQuantity()).isEqualByComparingTo("12500");
            assertThatThrownBy(()->packing.finalizePacking(ConsentEnvironment.DEV,f.branch(),f.enquiry(),"manager",actual)).hasMessageContaining("final invoice");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM occasion_packing_finalizations WHERE enquiry_id=?",Integer.class,f.enquiry())).isEqualTo(1);
            jdbc.update("UPDATE occasion_payment_attempts SET amount=2910 WHERE enquiry_id=?",f.enquiry());
            when(phonePe.createPayment(anyString(),eq(new BigDecimal("8940.00")),anyString(),eq(600)))
                .thenAnswer(invocation->new PhonePeClient.CreatePaymentResponse("final-balance",invocation.getArgument(0),"PENDING","https://pay.example/checkout",null,null));
            var balance=commitments.beginBalance(ConsentEnvironment.DEV,f.subject(),f.enquiry());
            commitments.verifiedWebhook(merchant(balance.attemptId()),"checkout.order.completed","COMPLETED","packing-balance");
            var confirmed=enquiries.staffGet(ConsentEnvironment.DEV,f.branch(),f.enquiry());
            assertThat(confirmed.status()).isEqualTo("CONFIRMED");assertThat(confirmed.paidAmount()).isEqualByComparingTo("11850");
            assertThat(jdbc.queryForObject("SELECT weight_grams FROM order_items oi JOIN occasion_enquiries e ON e.order_id=oi.order_id WHERE e.id=?",Integer.class,f.enquiry())).isEqualTo(12500);
        }finally{features.setOccasionBulkProduction(bulk);features.setOccasionEnquiries(enabled);}
    }

    @Test
    void lowerFinalWeightPreservesCollectedMoneyAndCreatesCreditReviewWithoutRefund() {
        var f=paidDeposit();var today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        jdbc.update("UPDATE products SET sale_mode='WEIGHT' WHERE id=?",f.product());
        jdbc.update("UPDATE occasion_enquiry_items SET requested_quantity=600,approved_quantity=12000,approved_unit='GRAM' WHERE enquiry_id=?",f.enquiry());
        var line=new OccasionQuoteCalculator.Line(f.product(),"Occasion sweets","GRAM",new BigDecimal("600"),"PIECE",new BigDecimal("400"),null,new BigDecimal("12000"),new BigDecimal("2.5"),new BigDecimal("2.5"),new BigDecimal("4800"),new BigDecimal("240"),new BigDecimal("6600"),new BigDecimal("11640"));
        var estimate=new OccasionQuoteCalculator.Calculation(java.util.List.of(line),line.foodBase(),line.foodTax(),line.packagingAmount(),line.grossAmount(),new BigDecimal("11058"),new BigDecimal("582"),clock.instant().minusSeconds(60),today.atTime(23,59,59).atZone(ZoneId.of("Asia/Kolkata")).toInstant(),today.atTime(10,0),java.util.List.of(),true);
        jdbc.update("UPDATE occasion_enquiries SET estimated=TRUE,original_estimate=11640,quoted_amount=11640,deposit_amount=11058,paid_amount=11058,service_date=?,quote_calculation=?::jsonb WHERE id=?",Date.valueOf(today),new tools.jackson.databind.ObjectMapper().writeValueAsString(estimate),f.enquiry());
        jdbc.update("UPDATE occasion_quote_lines SET sale_mode='WEIGHT',quantity=1,weight_grams=12000 WHERE enquiry_id=?",f.enquiry());
        jdbc.update("UPDATE occasion_payment_attempts SET amount=11058 WHERE enquiry_id=?",f.enquiry());
        var slot=enquiries.staffGet(ConsentEnvironment.DEV,f.branch(),f.enquiry()).pickupSlotId();
        jdbc.update("UPDATE pickup_slots SET slot_date=?,end_time='23:59:59' WHERE id=?",Date.valueOf(today),slot);
        jdbc.update("INSERT INTO occasion_production_allocations(enquiry_id,product_id,quantity,unit,expected_ready_at,state,approved_by) VALUES(?,?,12000,'GRAM',?,'COMMITTED','manager')",f.enquiry(),f.product(),Timestamp.valueOf(today.atTime(10,0)));
        var result=packing.finalizePacking(ConsentEnvironment.DEV,f.branch(),f.enquiry(),"manager",new OccasionPackingFinalizer.Input(java.util.List.of(new OccasionPackingFinalizer.Packed(f.product(),new BigDecimal("10000"))),0,true));
        assertThat(result.quotedAmount()).isEqualByComparingTo("10800");assertThat(result.paidAmount()).isEqualByComparingTo("11058");
        assertThat(result.creditReviewAmount()).isEqualByComparingTo("258");assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT status FROM occasion_payment_attempts WHERE enquiry_id=?",String.class,f.enquiry())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM payments p JOIN occasion_enquiries e ON e.order_id=p.order_id WHERE e.id=?",BigDecimal.class,f.enquiry())).isEqualByComparingTo("11058");
    }

    @Test
    void reportsBackfillThenRefreshOnlyAffectedBusinessDaysAndCustomers() {
        var f=paidDeposit();
        jdbc.update("UPDATE occasion_payment_attempts SET amount=1000 WHERE enquiry_id=?",f.enquiry());
        jdbc.update("UPDATE occasion_enquiries SET paid_amount=1000,status='CONFIRMED' WHERE id=?",f.enquiry());
        var number=orders.create(f.enquiry(),ConsentEnvironment.DEV,f.subject(),f.branch(),
            enquiries.staffGet(ConsentEnvironment.DEV,f.branch(),f.enquiry()).pickupSlotId(),new BigDecimal("1000"));
        var watermark=Timestamp.from(clock.instant().plus(Duration.ofDays(1)));
        jdbc.update("UPDATE orders SET order_status='PICKED_UP',updated_at=? WHERE order_number=?",watermark,number);
        jdbc.update("DELETE FROM analytics_refresh_checkpoint");
        analytics.refreshChangedOrders();
        assertThat(jdbc.queryForObject("SELECT completed_orders FROM analytics_branch_daily WHERE branch_id=?",Long.class,f.branch())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT net_revenue FROM analytics_branch_daily WHERE branch_id=?",BigDecimal.class,f.branch())).isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("SELECT verification_status FROM customer_contacts WHERE id=(SELECT customer_contact_id FROM orders WHERE order_number=?)",String.class,number)).isEqualTo("VERIFIED");
        jdbc.update("UPDATE orders SET order_status='CANCELLED',updated_at=? WHERE order_number=?",Timestamp.from(watermark.toInstant().plusSeconds(1)),number);
        analytics.refreshChangedOrders();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_branch_daily WHERE branch_id=?",Integer.class,f.branch())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_customer_metrics WHERE customer_contact_id=(SELECT customer_contact_id FROM orders WHERE order_number=?)",Integer.class,number)).isZero();
        analytics.refreshChangedOrders();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM analytics_refresh_checkpoint",Integer.class)).isEqualTo(1);
    }

    @Test
    void mixedSweetsCanShareOnePlasticBoxCompartmentWithinReviewedCapacity() {
        var f=paidDeposit();boolean enabled=features.isOccasionEnquiries();
        try {
            features.setOccasionEnquiries(true);
            var box=catalogue.saveBox(f.branch(),new OccasionCatalogue.Box(null,"400 ml plastic box",null,"400 ml","Food-safe plastic",1,3,new BigDecimal("10"),"",1,true));
            var recipe=java.util.List.of(new OccasionCatalogue.Recipe(f.product(),1),new OccasionCatalogue.Recipe(f.product()+1,1),new OccasionCatalogue.Recipe(f.product()+2,1));
            var items=recipe.stream().map(x->new OccasionEnquiryService.Item(x.productId(),new BigDecimal("600"),OccasionEnquiryService.Unit.PIECE,"Assorted sweets")).toList();
            var result=catalogue.validateGift(f.branch(),LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(3),items,new OccasionCatalogue.GiftRequest(box.id(),600,recipe,true));
            assertThat(result.box().compartments()).isEqualTo(1);assertThat(result.recipe()).hasSize(3);assertThat(result.includeSpoons()).isTrue();
            assertThat(result.packagingEstimate()).isEqualByComparingTo("6000");
        }finally{features.setOccasionEnquiries(enabled);}
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
