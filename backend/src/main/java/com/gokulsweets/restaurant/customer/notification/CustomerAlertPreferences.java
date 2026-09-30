package com.gokulsweets.restaurant.customer.notification;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerAlertPreferences {
    private final JdbcTemplate jdbc;
    private final CustomerNotificationInbox inbox;
    private final EnhancementProperties features;
    private final WebPushTransport transport;
    private final WebPushProperties properties;

    public record Input(Boolean soundEnabled, Boolean quietHoursEnabled, Integer quietStartMinute, Integer quietEndMinute) {}
    public record Settings(boolean soundEnabled, boolean quietHoursEnabled, int quietStartMinute, int quietEndMinute,
                           String scopeId, boolean pushConfigured, String applicationServerKey) {}
    public record SubscriptionInput(String endpoint, String publicKey, String authSecret) {}
    public record SubscriptionResult(UUID id) {}

    public boolean enabled() {return features.isNotificationAlerts() && inbox.enabled();}

    @Transactional(readOnly = true)
    public Settings settings(String environment, UUID subject) {
        var stored = jdbc.query("""
                SELECT sound_enabled, quiet_hours_enabled, quiet_start_minute, quiet_end_minute
                FROM customer_notification_preferences WHERE environment = ? AND subject_id = ?
                """, (rs, row) -> new Settings(rs.getBoolean(1), rs.getBoolean(2), rs.getInt(3), rs.getInt(4),
                subject.toString(), transport.configured(), transport.configured() ? properties.getPublicKey() : null), environment, subject);
        return stored.stream().findFirst().orElse(new Settings(false, true, 1320, 480,
                subject.toString(), transport.configured(), transport.configured() ? properties.getPublicKey() : null));
    }

    @Transactional
    public Settings save(String environment, UUID subject, Input input) {
        if (input == null || input.soundEnabled() == null || input.quietHoursEnabled() == null
                || input.quietStartMinute() == null || input.quietEndMinute() == null
                || input.quietStartMinute() < 0 || input.quietStartMinute() > 1439
                || input.quietEndMinute() < 0 || input.quietEndMinute() > 1439
                || input.quietHoursEnabled() && input.quietStartMinute().equals(input.quietEndMinute()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose distinct quiet-hours start and end times (IST).");
        lockSubject(environment, subject);
        jdbc.update("""
                INSERT INTO customer_notification_preferences(environment, subject_id, sound_enabled,
                    quiet_hours_enabled, quiet_start_minute, quiet_end_minute) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (environment, subject_id) DO UPDATE SET sound_enabled = EXCLUDED.sound_enabled,
                    quiet_hours_enabled = EXCLUDED.quiet_hours_enabled, quiet_start_minute = EXCLUDED.quiet_start_minute,
                    quiet_end_minute = EXCLUDED.quiet_end_minute, updated_at = CURRENT_TIMESTAMP
                """, environment, subject, input.soundEnabled(), input.quietHoursEnabled(), input.quietStartMinute(), input.quietEndMinute());
        return settings(environment, subject);
    }

    @Transactional
    public SubscriptionResult subscribe(String environment, UUID subject, String token, SubscriptionInput input) {
        if (!transport.configured()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Push is not configured. Your inbox remains available.");
        if (input == null || !WebPushTransport.validEndpoint(input.endpoint())
                || !WebPushTransport.validRecipientKeys(input.publicKey(), input.authSecret()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid browser subscription.");
        lockSubject(environment, subject);
        Long session = jdbc.query("""
                SELECT id FROM verified_customer_sessions WHERE environment = ? AND verified_subject_id = ?
                  AND token_digest = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP FOR SHARE
                """, rs -> rs.next() ? rs.getLong(1) : null, environment, subject, digest(token));
        if (session == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String endpointHash = HexFormat.of().formatHex(digest(input.endpoint()));
        var existing = jdbc.query("""
                SELECT s.id, s.subject_id, s.revoked_at IS NOT NULL OR v.revoked_at IS NOT NULL OR v.expires_at <= CURRENT_TIMESTAMP AS inactive, s.session_id, s.public_key, s.auth_secret
                FROM customer_push_subscriptions s JOIN verified_customer_sessions v ON v.id = s.session_id
                WHERE s.environment = ? AND s.endpoint_hash = ? FOR UPDATE OF s
                """, (rs, row) -> new Existing((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getBoolean(3), rs.getLong(4), rs.getString(5), rs.getString(6)), environment, endpointHash);
        if (!existing.isEmpty() && !existing.getFirst().subject().equals(subject) && !existing.getFirst().inactive())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This browser is registered to another account. Disable its old subscription first.");
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM customer_push_subscriptions s JOIN verified_customer_sessions v ON v.id = s.session_id WHERE s.environment = ? AND s.subject_id = ? AND s.revoked_at IS NULL AND v.revoked_at IS NULL AND v.expires_at > CURRENT_TIMESTAMP AND s.endpoint_hash <> ?",
                Long.class, environment, subject, endpointHash);
        if (count != null && count >= 5) throw new ResponseStatusException(HttpStatus.CONFLICT, "Maximum five browsers per account.");
        if (!existing.isEmpty() && !existing.getFirst().inactive() && existing.getFirst().subject().equals(subject)
                && existing.getFirst().sessionId() == session && existing.getFirst().publicKey().equals(input.publicKey())
                && existing.getFirst().auth().equals(input.authSecret())) return new SubscriptionResult(existing.getFirst().id());
        if (!existing.isEmpty()) {
            var id = existing.getFirst().id();
            // Rotate identity by creating a new subscription ID; old deliveries cannot target a new owner.
            jdbc.update("UPDATE customer_push_subscriptions SET revoked_at = CURRENT_TIMESTAMP WHERE id = ?", id);
            jdbc.update("UPDATE customer_push_subscriptions SET endpoint_hash = ? WHERE id = ?", "retired:" + id, id);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_push_subscriptions(id, environment, subject_id, session_id, endpoint,
                    endpoint_hash, public_key, auth_secret) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, environment, subject, session, input.endpoint(), endpointHash, input.publicKey(), input.authSecret());
        return new SubscriptionResult(id);
    }

    @Transactional
    public void unsubscribe(String environment, UUID subject, UUID id) {
        if (jdbc.update("UPDATE customer_push_subscriptions SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP) WHERE id = ? AND environment = ? AND subject_id = ?",
                id, environment, subject) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private void lockSubject(String environment, UUID subject) {
        if (jdbc.query("SELECT 1 FROM verified_customer_subjects WHERE environment = ? AND id = ? FOR UPDATE", (rs, row) -> rs.getInt(1), environment, subject).isEmpty())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    static byte[] digest(String value) {
        try {return MessageDigest.getInstance("SHA-256").digest(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));}
        catch (Exception unavailable) {throw new IllegalStateException("Hash unavailable");}
    }

    public static boolean quiet(boolean enabled, int start, int end, Instant now) {
        if (!enabled) return false;
        var time = now.atZone(ZoneId.of("Asia/Kolkata"));
        int minute = time.getHour() * 60 + time.getMinute();
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }
    private record Existing(UUID id, UUID subject, boolean inactive, long sessionId, String publicKey, String auth) {}
}
