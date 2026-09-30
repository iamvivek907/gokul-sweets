package com.gokulsweets.restaurant.customer.notification;

import com.gokulsweets.restaurant.config.ApplicationClock;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.dto.admin.UpdateOrderDelayRequest;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.service.AdminOrderQueryService;
import com.gokulsweets.restaurant.order.service.AdminOrderWorkflowService;
import com.gokulsweets.restaurant.order.service.OrderDelayService;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "gokul.features.notification-inbox=true",
        "gokul.identity.provider-abuse-controls-verified=true",
        "gokul.environment-isolation.environment=DEV",
        "gokul.environment-isolation.enabled=true",
        "gokul.web.environment-cors-enabled=true",
        "gokul.environment-isolation.api-origin=https://api-inbox-ci.example.invalid",
        "phonepe.redirect-url=https://dev.gokulsweets.in/checkout",
        "phonepe.webhook-url=https://api-inbox-ci.example.invalid/api/payments/webhooks/phonepe",
        "phonepe.webhook-checksum-key-id=inbox-test-key",
        "cloudflare.r2.bucket-name=gokul-inbox-test",
        "cloudflare.r2.public-url=https://images-inbox-ci.example.invalid"
})
@Transactional
class CustomerNotificationInboxIntegrationTest {
    @Autowired CustomerNotificationInbox inbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired EnhancementProperties features;
    @Autowired AdminOrderWorkflowService workflow;
    @Autowired OrderDelayService delays;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean StaffAuthorizationService staff;
    @MockitoBean AdminOrderQueryService queries;
    @MockitoSpyBean ApplicationClock clock;
    private final UUID subject = UUID.randomUUID();

    @BeforeEach
    void setup() {
        features.setNotificationInbox(true);
        when(clock.now()).thenReturn(LocalDateTime.of(2026, 9, 30, 10, 0));
    }

    @Test
    void realReadyTransitionCommitsOneEventAndReplayOrWrongStateCannotInventAnother() {
        var fixture = fixture("PREPARING");
        workflow.transitionStatus(fixture.number(), OrderStatus.READY_FOR_PICKUP);
        workflow.transitionStatus(fixture.number(), OrderStatus.READY_FOR_PICKUP);
        inbox.orderReady(fixture.id());
        var page = inbox.page("DEV", subject, null);
        assertThat(page.messages()).hasSize(1);
        assertThat(page.messages().getFirst().kind()).isEqualTo("READY_FOR_PICKUP");
        assertThat(page.messages().getFirst().targetId()).isEqualTo(fixture.number());
        assertThat(page.messages().getFirst().deliveryState()).isEqualTo("AVAILABLE");
        assertThat(page.messages().getFirst().createdAt()).isNotNull();
        assertThatThrownBy(() -> workflow.transitionStatus(fixture.number(), OrderStatus.CONFIRMED))
                .isInstanceOf(IllegalStateException.class);
        assertThat(inbox.page("DEV", subject, null).unreadCount()).isEqualTo(1);
    }

    @Test
    void eachPersistedStageHasUsefulCopyAndReplaysStayUnique() {
        var fixture = fixture("CONFIRMED");
        for (String stage : new String[]{"CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP"}) {
            jdbc.update("UPDATE orders SET order_status = ? WHERE id = ?", stage, fixture.id());
            inbox.orderReady(fixture.id()); inbox.orderReady(fixture.id());
            var message = inbox.page("DEV", subject, null).messages().getFirst();
            assertThat(message.kind()).isEqualTo(stage);
            assertThat(message.message()).contains(fixture.number());
            if (stage.equals("CONFIRMED")) assertThat(message.message()).contains("IST", "Pickup booked");
            if (stage.equals("PREPARING")) assertThat(message.message()).contains("wait for the ready");
        }
        assertThat(inbox.page("DEV", subject, null).messages()).hasSize(4);
    }

    @Test
    void revisedEstimatesAreDistinctButExactStaffRetryDoesNotDuplicate() {
        var fixture = fixture("CONFIRMED");
        var revised = LocalDateTime.of(2026, 9, 30, 12, 0);
        delays.report(fixture.number(), new UpdateOrderDelayRequest(revised, "Kitchen requires extra preparation time"));
        delays.report(fixture.number(), new UpdateOrderDelayRequest(revised, "Kitchen requires extra preparation time"));
        assertThat(inbox.page("DEV", subject, null).messages()).hasSize(1);
        delays.report(fixture.number(), new UpdateOrderDelayRequest(revised.plusHours(1), "Kitchen requires extra preparation time"));
        assertThat(inbox.page("DEV", subject, null).messages()).hasSize(2);
    }

    @Test
    void pendingPaymentAndUnownedOrderHaveNoReceiptAndRefundReviewIsNotRefundSuccess() {
        var fixture = fixture("CONFIRMED");
        Long payment = jdbc.queryForObject("INSERT INTO payments(order_id, provider, amount, payment_status) VALUES (?, 'PHONEPE', 100, 'PENDING') RETURNING id", Long.class, fixture.id());
        inbox.paymentChanged(payment);
        assertThat(inbox.page("DEV", subject, null).messages()).isEmpty();
        jdbc.update("UPDATE payments SET payment_status = 'PAID' WHERE id = ?", payment);
        inbox.paymentChanged(payment); inbox.paymentChanged(payment);
        jdbc.update("UPDATE payments SET payment_status = 'REFUND_PENDING' WHERE id = ?", payment);
        inbox.paymentChanged(payment);
        assertThat(inbox.page("DEV", subject, null).messages()).hasSize(2);
        assertThat(inbox.page("DEV", subject, null).messages().getFirst().title()).contains("review");
        jdbc.update("UPDATE payments SET payment_status = 'REFUNDED' WHERE id = ?", payment);
        inbox.paymentChanged(payment);
        assertThat(inbox.page("DEV", subject, null).messages().getFirst().title()).isEqualTo("Refund completed");
        jdbc.update("DELETE FROM verified_order_ownership WHERE order_id = ?", fixture.id());
        jdbc.update("DELETE FROM customer_notification_events WHERE subject_id = ?", subject);
        inbox.paymentChanged(payment);
        assertThat(inbox.page("DEV", subject, null).messages()).isEmpty();
    }

    @Test
    void customerAndEnvironmentIsolationReadRetriesAndKeysetPagination() {
        var fixture = fixture("READY_FOR_PICKUP");
        for (int i = 0; i < 35; i++) jdbc.update("""
                INSERT INTO customer_notification_events(environment, subject_id, event_key, kind, target_type, target_id, title, message)
                VALUES ('DEV', ?, ?, 'TEST', 'ORDER', ?, 'Test update', 'Open current order')
                """, subject, "test:" + i, fixture.number());
        var first = inbox.page("DEV", subject, null);
        assertThat(first.messages()).hasSize(30);
        var second = inbox.page("DEV", subject, first.nextBefore());
        assertThat(second.messages()).hasSize(5);
        assertThat(second.nextBefore()).isNull();
        assertThat(second.messages()).noneMatch(message -> first.messages().stream().anyMatch(existing -> existing.id() == message.id()));
        assertThat(inbox.page("DEV", UUID.randomUUID(), null).messages()).isEmpty();
        assertThat(inbox.page("PROD", subject, null).messages()).isEmpty();
        long id = first.messages().getFirst().id();
        assertThatThrownBy(() -> inbox.markRead("DEV", UUID.randomUUID(), id)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> inbox.markRead("PROD", subject, id)).isInstanceOf(ResponseStatusException.class);
        inbox.markRead("DEV", subject, id);
        var readAt = inbox.page("DEV", subject, null).messages().getFirst().readAt();
        inbox.markRead("DEV", subject, id);
        assertThat(inbox.page("DEV", subject, null).messages().getFirst().readAt()).isEqualTo(readAt);
        assertThat(inbox.page("DEV", subject, null).unreadCount()).isEqualTo(34);
    }

    @Test
    void flagOffSuppressesNewEventsAndOptionalChannelCannotGrantConsent() {
        var fixture = fixture("READY_FOR_PICKUP");
        features.setNotificationInbox(false);
        try {
            inbox.orderReady(fixture.id());
            assertThat(inbox.page("DEV", subject, null).messages()).isEmpty();
        } finally {features.setNotificationInbox(true);}
        assertThat(inbox.preferences("DEV", subject).offerInboxEnabled()).isFalse();
        assertThatThrownBy(() -> inbox.savePreferences("DEV", subject, new CustomerNotificationInbox.PreferenceInput(true)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(inbox.savePreferences("DEV", subject, new CustomerNotificationInbox.PreferenceInput(false)).offerInboxEnabled()).isFalse();
    }

    @Test
    void sourceAndMessageRollBackTogether() {
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String eventKey = "rollback:" + UUID.randomUUID();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            var fixture = fixture("READY_FOR_PICKUP");
            inbox.orderReady(fixture.id());
            assertThat(inbox.page("DEV", subject, null).messages()).hasSize(1);
            jdbc.update("UPDATE customer_notification_events SET event_key = ? WHERE subject_id = ?", eventKey, subject);
            throw new IllegalStateException("Source transaction failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_notification_events WHERE event_key = ?", Long.class, eventKey)).isZero();
    }

    @Test
    void occasionReceiptLinksExactRequestAndLateReviewDoesNotConfirmBooking() {
        var fixture = fixture("CONFIRMED");
        Long branch = jdbc.queryForObject("SELECT branch_id FROM orders WHERE id = ?", Long.class, fixture.id());
        UUID enquiry = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO occasion_enquiries(id, environment, subject_id, branch_id, occasion_type,
                    service_date, guest_count, fulfilment, status)
                VALUES (?, 'DEV', ?, ?, 'Family celebration', '2026-10-05', 20, 'PICKUP', 'PAID')
                """, enquiry, subject, branch);
        String merchant = "INBOX-OCCASION-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO occasion_payment_attempts(id, enquiry_id, environment, stage, status, amount,
                    merchant_order_id, expires_at)
                VALUES (?, ?, 'DEV', 'DEPOSIT', 'PENDING', 100, ?, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """, UUID.randomUUID(), enquiry, merchant);
        inbox.occasionPaymentChanged(merchant);
        assertThat(inbox.page("DEV", subject, null).messages()).isEmpty();
        jdbc.update("UPDATE occasion_payment_attempts SET status = 'PAID' WHERE merchant_order_id = ?", merchant);
        inbox.occasionPaymentChanged(merchant); inbox.occasionPaymentChanged(merchant);
        var receipt = inbox.page("DEV", subject, null).messages().getFirst();
        assertThat(receipt.targetType()).isEqualTo("OCCASION");
        assertThat(receipt.targetId()).isEqualTo(enquiry.toString());
        assertThat(receipt.title()).isEqualTo("Occasion deposit received");
        assertThat(inbox.page("DEV", subject, null).messages()).hasSize(1);
        jdbc.update("UPDATE occasion_payment_attempts SET status = 'REFUND_PENDING' WHERE merchant_order_id = ?", merchant);
        inbox.occasionPaymentChanged(merchant);
        assertThat(inbox.page("DEV", subject, null).messages().getFirst().title()).contains("review");
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentReadyRecordingProducesOneDurableMessage() throws Exception {
        var transaction = new TransactionTemplate(transactions);
        Fixture fixture = transaction.execute(status -> fixture("READY_FOR_PICKUP"));
        assertThat(fixture).isNotNull();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var task = (java.util.concurrent.Callable<Void>) () -> {
                start.await();
                transaction.executeWithoutResult(status -> inbox.orderReady(fixture.id()));
                return null;
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(inbox.page("DEV", subject, null).messages()).hasSize(1);
        } finally {
            executor.shutdownNow();
            transaction.executeWithoutResult(status -> {
                Long branch = jdbc.queryForObject("SELECT branch_id FROM orders WHERE id = ?", Long.class, fixture.id());
                Long slot = jdbc.queryForObject("SELECT pickup_slot_id FROM orders WHERE id = ?", Long.class, fixture.id());
                jdbc.update("DELETE FROM customer_notification_events WHERE subject_id = ?", subject);
                jdbc.update("DELETE FROM verified_order_ownership WHERE order_id = ?", fixture.id());
                jdbc.update("DELETE FROM orders WHERE id = ?", fixture.id());
                jdbc.update("DELETE FROM pickup_slots WHERE id = ?", slot);
                jdbc.update("DELETE FROM branches WHERE id = ?", branch);
            });
        }
    }

    @Test
    void searchAndUnreadFilterApplyBeforePaginationAcrossHundredsOfEvents() {
        fixture("CONFIRMED");
        jdbc.update("""
            INSERT INTO customer_notification_events(environment,subject_id,event_key,kind,target_type,target_id,title,message)
            SELECT 'DEV',?,'scale:'||n,'CONFIRMED','ORDER','SCALE-'||n,'Order update','Payment verified'
            FROM generate_series(1,105) n
            """,subject);
        var first=inbox.page("DEV",subject,null,false,"");
        assertThat(first.messages()).hasSize(30);assertThat(first.nextBefore()).isNotNull();
        var second=inbox.page("DEV",subject,first.nextBefore(),false,"");
        assertThat(second.messages()).hasSize(30);
        assertThat(second.messages()).noneMatch(m->first.messages().stream().anyMatch(previous->previous.id()==m.id()));
        var match=inbox.page("DEV",subject,null,false,"scale-105");
        assertThat(match.messages()).hasSize(1);
        inbox.markRead("DEV",subject,match.messages().getFirst().id());
        assertThat(inbox.page("DEV",subject,null,true,"scale-105").messages()).isEmpty();
        assertThat(inbox.page("DEV",UUID.randomUUID(),null,false,"scale").messages()).isEmpty();
    }

    private Fixture fixture(String status) {
        Long branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Inbox test') RETURNING id", Long.class, "INBOX-" + UUID.randomUUID().toString().substring(0, 8));
        Long slot = jdbc.queryForObject("INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time, capacity) VALUES (?, '2026-09-30', '11:00', '11:30', 20) RETURNING id", Long.class, branch);
        String number = "GKS-INBOX-" + UUID.randomUUID();
        Long order = jdbc.queryForObject("""
                INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name, customer_phone,
                    pickup_type, order_status, reservation_expires_at)
                VALUES (?, ?, ?, 'Test customer', '9876543210', 'NORMAL', ?, CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, number, branch, slot, status);
        jdbc.update("INSERT INTO verified_order_ownership(order_id, environment, verified_subject_id) VALUES (?, 'DEV', ?)", order, subject);
        return new Fixture(order, number);
    }
    private record Fixture(long id, String number) {}
}
