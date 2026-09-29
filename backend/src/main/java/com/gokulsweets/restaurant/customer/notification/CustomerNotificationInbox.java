package com.gokulsweets.restaurant.customer.notification;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.consent.ConsentPurpose;
import com.gokulsweets.restaurant.customer.consent.OptionalProcessingGate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable in-app delivery only. Source and inbox must commit or roll back together. */
@Service
@RequiredArgsConstructor
public class CustomerNotificationInbox {
    private final JdbcTemplate jdbc;
    private final EnhancementProperties features;
    private final Environment settings;
    private final OptionalProcessingGate optionalProcessing;

    public record Message(long id, String eventKey, String kind, String targetType, String targetId,
                          String title, String message, String deliveryState, Instant createdAt, Instant readAt) {}
    public record Page(List<Message> messages, long unreadCount, Long nextBefore) {}
    public record Preferences(boolean offerInboxEnabled, boolean marketingConsentGranted,
                              String transactionalChannel, boolean browserPushAvailable) {}
    public record PreferenceInput(Boolean offerInboxEnabled) {}

    public boolean enabled() {
        return features.isNotificationInbox() && features.isCustomerOtpIdentity()
                && settings.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)
                && settings.getProperty("gokul.web.environment-cors-enabled", Boolean.class, false)
                && settings.getProperty("gokul.identity.provider-abuse-controls-verified", Boolean.class, false)
                && List.of("DEV", "PROD").contains(environment());
    }

    private String environment() {
        return settings.getProperty("gokul.environment-isolation.environment", "");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentChanged(Long paymentId) {
        if (!enabled()) return;
        jdbc.update("""
                INSERT INTO customer_notification_events(environment, subject_id, event_key, kind,
                    target_type, target_id, title, message)
                SELECT own.environment, own.verified_subject_id, 'payment:' || p.id || ':' || p.payment_status,
                    'PAYMENT_' || p.payment_status, 'ORDER', o.order_number,
                    CASE p.payment_status WHEN 'PAID' THEN 'Payment received'
                         WHEN 'REFUNDED' THEN 'Refund completed' ELSE 'Payment needs refund review' END,
                    CASE p.payment_status WHEN 'PAID' THEN 'Your payment was verified. Open this order for its current fulfilment status.'
                         WHEN 'REFUNDED' THEN 'The payment provider confirmed your refund. Bank processing times may apply.'
                         ELSE 'This payment requires a refund review. This message does not confirm that money has been refunded.' END
                FROM payments p JOIN orders o ON o.id = p.order_id
                JOIN verified_order_ownership own ON own.order_id = o.id
                WHERE p.id = ? AND own.environment = ? AND p.payment_status IN ('PAID','REFUNDED','REFUND_PENDING')
                ON CONFLICT (environment, subject_id, event_key) DO NOTHING
                """, paymentId, environment());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void orderReady(Long orderId) {
        if (!enabled()) return;
        jdbc.update("""
                INSERT INTO customer_notification_events(environment, subject_id, event_key, kind,
                    target_type, target_id, title, message)
                SELECT own.environment, own.verified_subject_id, 'order:' || o.id || ':' || o.order_status,
                    o.order_status, 'ORDER', o.order_number,
                    CASE o.order_status WHEN 'READY_FOR_PICKUP' THEN 'Your order is ready for pickup'
                         ELSE 'Your order is ready for delivery' END,
                    CASE o.order_status WHEN 'READY_FOR_PICKUP' THEN 'The branch marked your order ready. Open the order for your pickup details.'
                         ELSE 'The kitchen marked your order ready. Open the order for the current rider status; delivery has not been confirmed.' END
                FROM orders o JOIN verified_order_ownership own ON own.order_id = o.id
                WHERE o.id = ? AND own.environment = ? AND o.order_status IN ('READY_FOR_PICKUP','READY_FOR_DELIVERY')
                ON CONFLICT (environment, subject_id, event_key) DO NOTHING
                """, orderId, environment());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delayChanged(Long orderId) {
        if (!enabled()) return;
        jdbc.update("""
                INSERT INTO customer_notification_events(environment, subject_id, event_key, kind,
                    target_type, target_id, title, message)
                SELECT own.environment, own.verified_subject_id,
                    'delay:' || o.id || ':' || md5(o.delay_reported_at::text || o.estimated_ready_at::text || o.delay_reason),
                    'READY_TIME_CHANGED', 'ORDER', o.order_number, 'Your ready-time estimate changed',
                    'The branch updated your ready-time estimate. Open the order for the latest time and explanation (IST).'
                FROM orders o JOIN verified_order_ownership own ON own.order_id = o.id
                WHERE o.id = ? AND own.environment = ? AND o.order_status IN ('CONFIRMED','PREPARING')
                  AND o.delay_reported_at IS NOT NULL AND o.estimated_ready_at IS NOT NULL AND o.delay_reason IS NOT NULL
                ON CONFLICT (environment, subject_id, event_key) DO NOTHING
                """, orderId, environment());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void occasionPaymentChanged(String merchantOrderId) {
        if (!enabled()) return;
        jdbc.update("""
                INSERT INTO customer_notification_events(environment, subject_id, event_key, kind,
                    target_type, target_id, title, message)
                SELECT e.environment, e.subject_id, 'occasion-payment:' || p.id || ':' || p.status,
                    'OCCASION_PAYMENT_' || p.status, 'OCCASION', e.id::text,
                    CASE WHEN p.status = 'REFUND_PENDING' THEN 'Occasion payment needs refund review'
                         WHEN p.stage = 'DEPOSIT' THEN 'Occasion deposit received' ELSE 'Occasion balance received' END,
                    CASE WHEN p.status = 'REFUND_PENDING' THEN 'This payment requires a refund review. Money has not been confirmed refunded.'
                         ELSE 'Your payment was verified. Open your occasion request for the current payment and fulfilment details.' END
                FROM occasion_payment_attempts p JOIN occasion_enquiries e ON e.id = p.enquiry_id
                WHERE p.merchant_order_id = ? AND e.environment = ? AND p.status IN ('PAID','REFUND_PENDING')
                ON CONFLICT (environment, subject_id, event_key) DO NOTHING
                """, merchantOrderId, environment());
    }

    @Transactional(readOnly = true)
    public Page page(String environment, UUID subject, Long before) {
        if (before != null && before <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var messages = jdbc.query("""
                SELECT * FROM customer_notification_events WHERE environment = ? AND subject_id = ?
                  AND id < ? ORDER BY id DESC LIMIT 31
                """, (rs, row) -> new Message(rs.getLong("id"), rs.getString("event_key"), rs.getString("kind"),
                rs.getString("target_type"), rs.getString("target_id"), rs.getString("title"), rs.getString("message"),
                rs.getString("delivery_state"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()),
                environment, subject, before == null ? Long.MAX_VALUE : before);
        Long unread = jdbc.queryForObject("SELECT COUNT(*) FROM customer_notification_events WHERE environment = ? AND subject_id = ? AND read_at IS NULL",
                Long.class, environment, subject);
        return new Page(messages.stream().limit(30).toList(), unread == null ? 0 : unread,
                messages.size() > 30 ? messages.get(29).id() : null);
    }

    @Transactional
    public void markRead(String environment, UUID subject, long id) {
        if (jdbc.update("""
                UPDATE customer_notification_events SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
                WHERE id = ? AND environment = ? AND subject_id = ?
                """, id, environment, subject) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @Transactional(readOnly = true)
    public Preferences preferences(String environment, UUID subject) {
        boolean optedIn = jdbc.query("SELECT offer_inbox_enabled FROM customer_notification_preferences WHERE environment = ? AND subject_id = ?",
                rs -> rs.next() && rs.getBoolean(1), environment, subject);
        boolean consent = optionalProcessing.allows(ConsentEnvironment.valueOf(environment), subject, ConsentPurpose.MARKETING);
        return new Preferences(optedIn, consent, "IN_APP", false);
    }

    @Transactional
    public Preferences savePreferences(String environment, UUID subject, PreferenceInput input) {
        if (input == null || input.offerInboxEnabled() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        // A channel preference never substitutes for versioned privacy consent.
        if (input.offerInboxEnabled() && !preferences(environment, subject).marketingConsentGranted())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Choose marketing consent in Privacy and data choices first.");
        jdbc.update("""
                INSERT INTO customer_notification_preferences(environment, subject_id, offer_inbox_enabled)
                VALUES (?, ?, ?) ON CONFLICT (environment, subject_id) DO UPDATE
                SET offer_inbox_enabled = EXCLUDED.offer_inbox_enabled, updated_at = CURRENT_TIMESTAMP
                """, environment, subject, input.offerInboxEnabled());
        return preferences(environment, subject);
    }
}
