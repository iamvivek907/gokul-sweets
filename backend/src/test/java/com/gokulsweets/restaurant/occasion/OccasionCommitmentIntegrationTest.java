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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "phonepe.redirect-url=https://ci.example.invalid/checkout")
@Transactional
class OccasionCommitmentIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OccasionCommitmentService commitments;
    @Autowired EnhancementProperties features;
    @Autowired Clock clock;
    @MockitoBean PhonePeClient phonePe;

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

    private String merchant(UUID attemptId) {
        return jdbc.queryForObject("SELECT merchant_order_id FROM occasion_payment_attempts WHERE id = ?",
                String.class, attemptId);
    }

    private Fixture paidDeposit() {
        UUID suffix = UUID.randomUUID();
        long branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Occasion test') RETURNING id",
                Long.class, "OCC-" + suffix.toString().substring(0, 8));
        UUID subject = UUID.randomUUID(), enquiry = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO occasion_enquiries(id, environment, subject_id, branch_id, occasion_type,
                    service_date, guest_count, fulfilment, status, quoted_amount, deposit_amount,
                    paid_amount, quote_terms, quote_expires_at, balance_due_at)
                VALUES (?, 'DEV', ?, ?, 'Celebration', ?, 20, 'PICKUP', 'PAID', 1000, 200,
                    200, 'Pickup only', ?, ?)
                """, enquiry, subject, branch,
                Date.valueOf(LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(2)),
                Timestamp.from(clock.instant().plus(Duration.ofHours(1))),
                Timestamp.from(clock.instant().plus(Duration.ofDays(1))));
        return new Fixture(enquiry, subject);
    }

    private record Fixture(UUID enquiry, UUID subject) {}
}
