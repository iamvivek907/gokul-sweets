package com.gokulsweets.restaurant.payment.service;

import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.payment.provider.*;
import com.gokulsweets.restaurant.payment.enums.*;
import com.gokulsweets.restaurant.payment.exception.PaymentGatewayException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"gokul.jobs.background-enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RefundReconciliationIntegrationTest {
    @Autowired PaymentRepository repository;
    @Autowired PaymentRefundService refunds;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PaymentProviderRegistry registry;
    @Autowired PaymentStatusService statuses;
    @MockitoBean com.gokulsweets.restaurant.loyalty.LoyaltyService loyalty;
    @MockitoBean com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox notifications;
    PaymentProvider provider;
    long branch, slot, order, payment;
    LocalDateTime now() {return LocalDateTime.now(ZoneId.of("Asia/Kolkata"));}

    @BeforeEach void setup() {
        String ref = "refund-" + UUID.randomUUID();
        branch = jdbc.queryForObject("INSERT INTO branches(code,name) VALUES(?,'Refund test') RETURNING id",Long.class,ref);
        slot = jdbc.queryForObject("INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity) VALUES(?,CURRENT_DATE,TIME '10:00',TIME '11:00',100) RETURNING id",Long.class,branch);
        order = jdbc.queryForObject("INSERT INTO orders(order_number,branch_id,pickup_slot_id,customer_name,customer_phone,pickup_type,order_status,subtotal,total_amount) VALUES(?,?,?,'Test','9876543210','NORMAL','CANCELLED',100,100) RETURNING id",Long.class,ref,branch,slot);
        payment = jdbc.queryForObject("INSERT INTO payments(order_id,provider,amount,refund_amount,payment_status,provider_order_id,refund_reference_id) VALUES(?,'PHONEPE',100,100,'REFUND_PENDING',?,?) RETURNING id",Long.class,order,ref,ref);
        provider = mock(PaymentProvider.class);
        when(registry.require(PaymentProviderType.PHONEPE)).thenReturn(provider);
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM payments WHERE id=?",payment);
        jdbc.update("DELETE FROM orders WHERE id=?",order);
        jdbc.update("DELETE FROM pickup_slots WHERE id=?",slot);
        jdbc.update("DELETE FROM branches WHERE id=?",branch);
    }
    @Test void onlyOneConcurrentSchedulerClaimsRefundAndExpiredLeaseRecovers() throws Exception {
        LocalDateTime started=now();
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> claim=()->{gate.await();return repository.claimRefundCheck(payment,started,started.plusMinutes(10));};
            var one=executor.submit(claim);var two=executor.submit(claim);gate.countDown();
            assertThat(one.get(10,TimeUnit.SECONDS)+two.get(10,TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(repository.findDueRefundIds(started,org.springframework.data.domain.PageRequest.of(0,100)))
                .doesNotContain(payment);
        assertThat(repository.claimRefundCheck(payment,started.plusMinutes(11),started.plusMinutes(21))).isEqualTo(1);
    }
    @Test void lostSubmissionAcknowledgementPersistsMarkerAndRetryBackoffAcrossServiceRestart() {
        when(provider.refund(any())).thenAnswer(call->{
            assertThat(repository.findById(payment).orElseThrow().getRefundSubmissionAttemptedAt()).isNotNull();
            throw new PaymentGatewayException("PHONEPE_REFUND_INVALID_RESPONSE","private payload",true);
        });
        refunds.processPendingRefund(payment);
        var first=repository.findById(payment).orElseThrow();
        assertThat(first.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(first.getRefundCheckFailures()).isEqualTo(1);
        assertThat(first.getRefundNextCheckAt()).isAfter(now());
        assertThat(first.getRefundFailureReason()).doesNotContain("private payload");
        var restarted=new PaymentRefundService(repository,statuses,registry);
        restarted.processPendingRefund(payment);
        verify(provider,times(1)).refund(any());
        jdbc.update("UPDATE payments SET refund_next_check_at=?,refund_check_failures=7 WHERE id=?",now().minusMinutes(1),payment);
        restarted.processPendingRefund(payment);
        var latest=repository.findById(payment).orElseThrow();
        assertThat(latest.getRefundCheckFailures()).isEqualTo(8);
        assertThat(latest.isRefundReviewRequired()).isTrue();
        assertThat(latest.getRefundNextCheckAt()).isAfter(now().plusMinutes(59));
        assertThat(latest.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    }
    @Test void duplicateCompletionAndLatePendingOrFailedResponsesCannotReopenRefund() {
        jdbc.update("UPDATE payments SET refund_review_required=true,refund_check_failures=8 WHERE id=?",payment);
        statuses.markRefunded(payment,"refund1");
        statuses.markRefunded(payment,"refund1");
        statuses.markRefundStillPending(payment,"refund1");
        statuses.markRefundInitiated(payment,"refund1");
        statuses.markRefundFailed(payment,"refund1","late failure");
        var result=repository.findById(payment).orElseThrow();
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.isRefundReviewRequired()).isFalse();
        assertThat(result.getRefundCheckFailures()).isZero();
        assertThat(result.getRefundedAt()).isNotNull();
        verify(notifications,times(1)).paymentChanged(payment);
        verify(loyalty,times(1)).reconcile(order);
    }

    @Test void staleClaimCannotChangeConfirmedRefundOrOverwriteNewLease() {
        LocalDateTime started=now(),lease=started.plusMinutes(10);
        assertThat(repository.claimRefundCheck(payment,started,lease)).isEqualTo(1);
        assertThat(repository.finishRefundCheck(payment,lease.plusMinutes(1),started,2,true,"uncertain",started)).isZero();
        jdbc.update("UPDATE payments SET payment_status='REFUNDED' WHERE id=?",payment);
        assertThat(repository.finishRefundCheck(payment,lease,started,2,true,"uncertain",started)).isZero();
        assertThat(repository.findById(payment).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
