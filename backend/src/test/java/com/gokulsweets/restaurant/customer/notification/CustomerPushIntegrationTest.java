package com.gokulsweets.restaurant.customer.notification;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.identity.VerifiedCustomerSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "gokul.features.notification-inbox=true", "gokul.features.notification-alerts=true",
        "gokul.notifications.web-push.scheduler-enabled=false",
        "gokul.identity.provider-abuse-controls-verified=true", "gokul.environment-isolation.environment=DEV",
        "gokul.environment-isolation.enabled=true", "gokul.web.environment-cors-enabled=true",
        "gokul.environment-isolation.api-origin=https://api-push-ci.example.invalid",
        "phonepe.redirect-url=https://dev.gokulsweets.in/checkout",
        "phonepe.webhook-url=https://api-push-ci.example.invalid/api/payments/webhooks/phonepe",
        "phonepe.webhook-checksum-key-id=push-test-key", "cloudflare.r2.bucket-name=gokul-push-test",
        "cloudflare.r2.public-url=https://images-push-ci.example.invalid"
})
@Transactional
class CustomerPushIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerAlertPreferences preferences;
    @Autowired CustomerPushDispatcher dispatcher;
    @Autowired CustomerNotificationInbox inbox;
    @Autowired VerifiedCustomerSessionStore sessions;
    @MockitoBean WebPushTransport transport;
    private final UUID subject = UUID.randomUUID();
    private String token;
    private String publicKey;

    @BeforeEach
    void setup() throws Exception {
        jdbc.update("INSERT INTO verified_customer_subjects(id, environment, verified_phone) VALUES (?, 'DEV', '+919876543210')", subject);
        token = sessions.issue(ConsentEnvironment.DEV, subject, Instant.now()).token();
        when(transport.configured()).thenReturn(true);
        // Valid P-256 public key; the test never sends externally.
        var generator = java.security.KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(((org.bouncycastle.jce.interfaces.ECPublicKey) pair.getPublic()).getQ().getEncoded(false));
    }

    private CustomerAlertPreferences.SubscriptionInput input(String suffix) {
        return new CustomerAlertPreferences.SubscriptionInput("https://fcm.googleapis.com/fcm/send/" + suffix,
                publicKey, Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
    }

    private long event() {
        return jdbc.queryForObject("""
                INSERT INTO customer_notification_events(environment, subject_id, event_key, kind, target_type, target_id, title, message)
                VALUES ('DEV', ?, ?, 'PAYMENT_PAID', 'ORDER', 'TEST-ORDER', 'Payment received', 'Open inbox') RETURNING id
                """, Long.class, subject, "push-test:" + UUID.randomUUID());
    }

    @Test
    void idempotentRegistrationOwnerIsolationRevocationAndSessionExpiry() {
        var browser = input(UUID.randomUUID().toString());
        var registration = preferences.subscribe("DEV", subject, token, browser);
        assertThat(preferences.subscribe("DEV", subject, token, browser).id()).isEqualTo(registration.id());
        assertThatThrownBy(() -> preferences.unsubscribe("DEV", UUID.randomUUID(), registration.id())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> preferences.unsubscribe("PROD", subject, registration.id())).isInstanceOf(ResponseStatusException.class);
        preferences.unsubscribe("DEV", subject, registration.id());
        preferences.unsubscribe("DEV", subject, registration.id());
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM customer_push_subscriptions WHERE id = ?", Boolean.class, registration.id())).isTrue();
        sessions.revoke(ConsentEnvironment.DEV, token, Instant.now());
        assertThatThrownBy(() -> preferences.subscribe("DEV", subject, token, input("after-revocation"))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deliveryIsAcceptedOnceAndGoneEndpointIsRevokedWhileReadAndQuietEventsStayInInbox() throws Exception {
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(false, false, 1320, 480));
        var registration = preferences.subscribe("DEV", subject, token, input(UUID.randomUUID().toString()));
        long event = event();
        when(transport.send(anyString(), anyString(), anyString(), eq(event), anyString(), anyString(), anyString())).thenReturn(201);
        dispatcher.dispatchBatch(); dispatcher.dispatchBatch();
        verify(transport, times(1)).send(anyString(), anyString(), anyString(), eq(event), anyString(), anyString(), anyString());
        assertThat(jdbc.queryForObject("SELECT state FROM customer_push_deliveries WHERE event_id = ?", String.class, event)).isEqualTo("ACCEPTED");
        long gone = event();
        when(transport.send(anyString(), anyString(), anyString(), eq(gone), anyString(), anyString(), anyString())).thenReturn(410);
        dispatcher.dispatchBatch();
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM customer_push_subscriptions WHERE id = ?", Boolean.class, registration.id())).isTrue();
        long later = event(); dispatcher.dispatchBatch();
        verify(transport, never()).send(anyString(), anyString(), anyString(), eq(later), anyString(), anyString(), anyString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_notification_events WHERE subject_id = ?", Long.class, subject)).isEqualTo(3);
    }

    @Test
    void stalePreparationPushIsSkippedWhenOrderAlreadyReadyAndLatestReadyCopyIsSent() throws Exception {
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(false, false, 1320, 480));
        preferences.subscribe("DEV", subject, token, input(UUID.randomUUID().toString()));
        Long branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Push branch') RETURNING id", Long.class, "PUSH-" + UUID.randomUUID().toString().substring(0, 8));
        Long slot = jdbc.queryForObject("INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time, capacity) VALUES (?, '2026-10-01', '18:00', '18:30', 20) RETURNING id", Long.class, branch);
        String number = "PUSH-" + UUID.randomUUID();
        Long order = jdbc.queryForObject("""
                INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name, customer_phone,
                    pickup_type, order_status, reservation_expires_at)
                VALUES (?, ?, ?, 'Test', '9876543210', 'NORMAL', 'PREPARING', CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, number, branch, slot);
        jdbc.update("INSERT INTO verified_order_ownership(order_id, environment, verified_subject_id) VALUES (?, 'DEV', ?)", order, subject);
        inbox.orderReady(order);
        long old = inbox.page("DEV", subject, null).messages().getFirst().id();
        jdbc.update("UPDATE orders SET order_status = 'READY_FOR_PICKUP' WHERE id = ?", order);
        inbox.orderReady(order);
        long ready = inbox.page("DEV", subject, null).messages().getFirst().id();
        when(transport.send(anyString(), anyString(), anyString(), eq(ready), anyString(), anyString(), anyString())).thenReturn(201);
        dispatcher.dispatchBatch();
        verify(transport, never()).send(anyString(), anyString(), anyString(), eq(old), anyString(), anyString(), anyString());
        verify(transport).send(anyString(), anyString(), anyString(), eq(ready), eq("Your order is ready for pickup"), contains("Push branch"), eq("/orders/" + number));
        assertThat(inbox.page("DEV", subject, null).messages()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT state FROM customer_push_deliveries WHERE event_id = ?", String.class, old)).isEqualTo("SKIPPED");
    }

    @Test
    void completionIsAutomaticallyReadAndSendsOneOptionalReviewPush() throws Exception {
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(false, false, 1320, 480));
        preferences.subscribe("DEV", subject, token, input(UUID.randomUUID().toString()));
        Long branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Push branch') RETURNING id", Long.class, "PUSH-" + UUID.randomUUID().toString().substring(0, 8));
        Long slot = jdbc.queryForObject("INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time, capacity) VALUES (?, '2026-10-01', '18:00', '18:30', 20) RETURNING id", Long.class, branch);
        String number = "PUSH-" + UUID.randomUUID();
        Long order = jdbc.queryForObject("""
                INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name, customer_phone,
                    pickup_type, order_status, reservation_expires_at)
                VALUES (?, ?, ?, 'Test', '9876543210', 'NORMAL', 'PREPARING', CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, number, branch, slot);
        jdbc.update("INSERT INTO verified_order_ownership(order_id, environment, verified_subject_id) VALUES (?, 'DEV', ?)", order, subject);
        inbox.orderReady(order);
        jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=?", order);
        inbox.orderReady(order);
        long completion = inbox.page("DEV", subject, null).messages().getFirst().id();
        assertThat(inbox.page("DEV", subject, null).unreadCount()).isZero();
        when(transport.send(anyString(), anyString(), anyString(), eq(completion), anyString(), anyString(), anyString())).thenReturn(201);
        dispatcher.dispatchBatch(); dispatcher.dispatchBatch();
        verify(transport, times(1)).send(anyString(), anyString(), anyString(), eq(completion), anyString(), contains("completely optional"), eq("/orders/"+number+"#order-review"));
    }

    @Test
    void explicitAcknowledgementCancelsUnsentOptionalCompletionPush() throws Exception {
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(false, false, 1320, 480));
        preferences.subscribe("DEV", subject, token, input(UUID.randomUUID().toString()));
        Long branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Push branch') RETURNING id", Long.class, "PUSH-" + UUID.randomUUID().toString().substring(0, 8));
        Long slot = jdbc.queryForObject("INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time, capacity) VALUES (?, '2026-10-01', '18:00', '18:30', 20) RETURNING id", Long.class, branch);
        String number = "PUSH-" + UUID.randomUUID();
        Long order = jdbc.queryForObject("""
                INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name, customer_phone,
                    pickup_type, order_status, reservation_expires_at)
                VALUES (?, ?, ?, 'Test', '9876543210', 'NORMAL', 'PREPARING', CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, number, branch, slot);
        jdbc.update("INSERT INTO verified_order_ownership(order_id, environment, verified_subject_id) VALUES (?, 'DEV', ?)", order, subject);
        jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=?", order);
        inbox.orderReady(order);
        inbox.markTargetRead("DEV", subject, "ORDER", number, null);
        dispatcher.dispatchBatch();
        verify(transport, never()).send(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void existingReviewSuppressesUnsentCompletionInvitation() throws Exception {
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(false, false, 1320, 480));
        preferences.subscribe("DEV", subject, token, input(UUID.randomUUID().toString()));
        Long branch = jdbc.queryForObject("INSERT INTO branches(code, name) VALUES (?, 'Push branch') RETURNING id", Long.class, "PUSH-" + UUID.randomUUID().toString().substring(0, 8));
        Long slot = jdbc.queryForObject("INSERT INTO pickup_slots(branch_id, slot_date, start_time, end_time, capacity) VALUES (?, '2026-10-01', '18:00', '18:30', 20) RETURNING id", Long.class, branch);
        String number = "PUSH-" + UUID.randomUUID();
        Long order = jdbc.queryForObject("""
                INSERT INTO orders(order_number, branch_id, pickup_slot_id, customer_name, customer_phone,
                    pickup_type, order_status, reservation_expires_at)
                VALUES (?, ?, ?, 'Test', '9876543210', 'NORMAL', 'PREPARING', CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, number, branch, slot);
        jdbc.update("INSERT INTO verified_order_ownership(order_id, environment, verified_subject_id) VALUES (?, 'DEV', ?)", order, subject);
        jdbc.update("UPDATE orders SET order_status='PICKED_UP' WHERE id=?", order);
        inbox.orderReady(order);
        jdbc.update("INSERT INTO reviews(order_id,branch_id,overall_rating,created_at,updated_at) VALUES (?,?,5,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", order,branch);
        dispatcher.dispatchBatch();
        verify(transport, never()).send(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void temporaryErrorsBackOffAndRetriesStopAfterThreeAttempts() throws Exception {
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(false, false, 1320, 480));
        preferences.subscribe("DEV", subject, token, input(UUID.randomUUID().toString()));
        long event = event();
        when(transport.send(anyString(), anyString(), anyString(), eq(event), anyString(), anyString(), anyString())).thenReturn(503);
        dispatcher.dispatchBatch(); dispatcher.dispatchBatch();
        verify(transport, times(1)).send(anyString(), anyString(), anyString(), eq(event), anyString(), anyString(), anyString());
        for (int i = 0; i < 2; i++) {
            jdbc.update("UPDATE customer_push_deliveries SET next_attempt_at = CURRENT_TIMESTAMP WHERE event_id = ?", event);
            dispatcher.dispatchBatch();
        }
        assertThat(jdbc.queryForObject("SELECT state FROM customer_push_deliveries WHERE event_id = ?", String.class, event)).isEqualTo("FAILED");
        verify(transport, times(3)).send(anyString(), anyString(), anyString(), eq(event), anyString(), anyString(), anyString());
    }

    @Test
    void quietHoursAndReadEventsNeverSendAndInvalidTimesAreRejected() throws Exception {
        var now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        int minute = now.getHour() * 60 + now.getMinute();
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(true, true, (minute + 1435) % 1440, (minute + 5) % 1440));
        preferences.subscribe("DEV", subject, token, input(UUID.randomUUID().toString()));
        long event = event(); dispatcher.dispatchBatch();
        verify(transport, never()).send(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
        assertThat(jdbc.queryForObject("SELECT state FROM customer_push_deliveries WHERE event_id = ?", String.class, event)).isEqualTo("SKIPPED");
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(true, false, 1320, 480));
        long read = event();
        jdbc.update("UPDATE customer_notification_events SET read_at = CURRENT_TIMESTAMP WHERE id = ?", read);
        dispatcher.dispatchBatch();
        verify(transport, never()).send(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> preferences.save("DEV", subject, new CustomerAlertPreferences.Input(true, true, 500, 500))).isInstanceOf(ResponseStatusException.class);
        assertThat(preferences.settings("PROD", subject).soundEnabled()).isFalse();
    }
}
