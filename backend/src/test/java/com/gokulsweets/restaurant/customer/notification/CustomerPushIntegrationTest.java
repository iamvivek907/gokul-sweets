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
        when(transport.send(anyString(), anyString(), anyString(), eq(event))).thenReturn(201);
        dispatcher.dispatchBatch(); dispatcher.dispatchBatch();
        verify(transport, times(1)).send(anyString(), anyString(), anyString(), eq(event));
        assertThat(jdbc.queryForObject("SELECT state FROM customer_push_deliveries WHERE event_id = ?", String.class, event)).isEqualTo("ACCEPTED");
        long gone = event();
        when(transport.send(anyString(), anyString(), anyString(), eq(gone))).thenReturn(410);
        dispatcher.dispatchBatch();
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM customer_push_subscriptions WHERE id = ?", Boolean.class, registration.id())).isTrue();
        long later = event(); dispatcher.dispatchBatch();
        verify(transport, never()).send(anyString(), anyString(), anyString(), eq(later));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_notification_events WHERE subject_id = ?", Long.class, subject)).isEqualTo(3);
    }

    @Test
    void temporaryErrorsBackOffAndRetriesStopAfterThreeAttempts() throws Exception {
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(false, false, 1320, 480));
        preferences.subscribe("DEV", subject, token, input(UUID.randomUUID().toString()));
        long event = event();
        when(transport.send(anyString(), anyString(), anyString(), eq(event))).thenReturn(503);
        dispatcher.dispatchBatch(); dispatcher.dispatchBatch();
        verify(transport, times(1)).send(anyString(), anyString(), anyString(), eq(event));
        for (int i = 0; i < 2; i++) {
            jdbc.update("UPDATE customer_push_deliveries SET next_attempt_at = CURRENT_TIMESTAMP WHERE event_id = ?", event);
            dispatcher.dispatchBatch();
        }
        assertThat(jdbc.queryForObject("SELECT state FROM customer_push_deliveries WHERE event_id = ?", String.class, event)).isEqualTo("FAILED");
        verify(transport, times(3)).send(anyString(), anyString(), anyString(), eq(event));
    }

    @Test
    void quietHoursAndReadEventsNeverSendAndInvalidTimesAreRejected() throws Exception {
        var now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        int minute = now.getHour() * 60 + now.getMinute();
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(true, true, (minute + 1435) % 1440, (minute + 5) % 1440));
        preferences.subscribe("DEV", subject, token, input(UUID.randomUUID().toString()));
        long event = event(); dispatcher.dispatchBatch();
        verify(transport, never()).send(anyString(), anyString(), anyString(), anyLong());
        assertThat(jdbc.queryForObject("SELECT state FROM customer_push_deliveries WHERE event_id = ?", String.class, event)).isEqualTo("SKIPPED");
        preferences.save("DEV", subject, new CustomerAlertPreferences.Input(true, false, 1320, 480));
        long read = event();
        jdbc.update("UPDATE customer_notification_events SET read_at = CURRENT_TIMESTAMP WHERE id = ?", read);
        dispatcher.dispatchBatch();
        verify(transport, never()).send(anyString(), anyString(), anyString(), anyLong());
        assertThatThrownBy(() -> preferences.save("DEV", subject, new CustomerAlertPreferences.Input(true, true, 500, 500))).isInstanceOf(ResponseStatusException.class);
        assertThat(preferences.settings("PROD", subject).soundEnabled()).isFalse();
    }
}
