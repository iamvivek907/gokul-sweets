package com.gokulsweets.restaurant.customer.notification;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

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

/** Backend customer alert preferences contract and implementation. */
@Service
@RequiredArgsConstructor
public class CustomerAlertPreferences {

    private final JdbcTemplate jdbc;

    private final CustomerNotificationInbox inbox;

    private final EnhancementProperties features;

    private final WebPushTransport transport;

    private final WebPushProperties properties;

    /**
     * Immutable input data contract.
     *
     * @param soundEnabled the sound enabled
     * @param quietHoursEnabled the quiet hours enabled
     * @param quietStartMinute the quiet start minute
     * @param quietEndMinute the quiet end minute
     */
    public record Input(
            Boolean soundEnabled,
            Boolean quietHoursEnabled,
            Integer quietStartMinute,
            Integer quietEndMinute) {}

    /**
     * Immutable settings data contract.
     *
     * @param soundEnabled the sound enabled
     * @param quietHoursEnabled the quiet hours enabled
     * @param quietStartMinute the quiet start minute
     * @param quietEndMinute the quiet end minute
     * @param scopeId the scope id
     * @param pushConfigured the push configured
     * @param applicationServerKey the application server key
     */
    public record Settings(
            boolean soundEnabled,
            boolean quietHoursEnabled,
            int quietStartMinute,
            int quietEndMinute,
            String scopeId,
            boolean pushConfigured,
            String applicationServerKey) {}

    /**
     * Immutable subscription input data contract.
     *
     * @param endpoint the endpoint
     * @param publicKey the public key
     * @param authSecret the auth secret
     */
    public record SubscriptionInput(String endpoint, String publicKey, String authSecret) {}

    /**
     * Immutable subscription result data contract.
     *
     * @param id the id
     */
    public record SubscriptionResult(UUID id) {}

    /**
     * Returns whether the configured prerequisites for this feature are enabled.
     *
     * @return the value of {@code features.isNotificationAlerts() && inbox.enabled()}
     */
    public boolean enabled() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAlertPreferences.class, "enabled()");
        try {
            return features.isNotificationAlerts() && inbox.enabled();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerAlertPreferences.class, "enabled()");
        }
    }

    /**
     * Returns settings information for customer alert preferences.
     *
     * <p>Reads {@code customer_notification_preferences}.
     *
     * @param environment the environment supplied to this method
     * @param subject the subject supplied to this method
     * @return the {@code Settings} result
     */
    @Transactional(readOnly = true)
    public Settings settings(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAlertPreferences.class, "settings(String,UUID)");
        try {
            var stored =
                    jdbc.query(
                            """
SELECT sound_enabled, quiet_hours_enabled, quiet_start_minute, quiet_end_minute
FROM customer_notification_preferences WHERE environment = ? AND subject_id = ?
""",
                            (rs, row) ->
                                    new Settings(
                                            rs.getBoolean(1),
                                            rs.getBoolean(2),
                                            rs.getInt(3),
                                            rs.getInt(4),
                                            subject.toString(),
                                            transport.configured(),
                                            transport.configured()
                                                    ? properties.getPublicKey()
                                                    : null),
                            environment,
                            subject);
            return stored.stream()
                    .findFirst()
                    .orElse(
                            new Settings(
                                    false,
                                    true,
                                    1320,
                                    480,
                                    subject.toString(),
                                    transport.configured(),
                                    transport.configured() ? properties.getPublicKey() : null));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAlertPreferences.class,
                    "settings(String,UUID)");
        }
    }

    /**
     * Persists customer alert preferences data and returns the {@code Settings} result.
     *
     * <p>Writes {@code customer_notification_preferences}.
     *
     * @param environment the environment supplied to this method
     * @param subject the subject supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code settings(environment, subject)}
     * @throws ResponseStatusException when the method rejects the request with {@code Choose
     *     distinct quiet-hours start and end times (IST).}
     */
    @Transactional
    public Settings save(String environment, UUID subject, Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAlertPreferences.class, "save(String,UUID,Input)");
        try {
            if (input == null
                    || input.soundEnabled() == null
                    || input.quietHoursEnabled() == null
                    || input.quietStartMinute() == null
                    || input.quietEndMinute() == null
                    || input.quietStartMinute() < 0
                    || input.quietStartMinute() > 1439
                    || input.quietEndMinute() < 0
                    || input.quietEndMinute() > 1439
                    || input.quietHoursEnabled()
                            && input.quietStartMinute().equals(input.quietEndMinute()))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Choose distinct quiet-hours start and end times (IST).");
            lockSubject(environment, subject);
            jdbc.update(
                    """
INSERT INTO customer_notification_preferences(environment, subject_id, sound_enabled,
    quiet_hours_enabled, quiet_start_minute, quiet_end_minute) VALUES (?, ?, ?, ?, ?, ?)
ON CONFLICT (environment, subject_id) DO UPDATE SET sound_enabled = EXCLUDED.sound_enabled,
    quiet_hours_enabled = EXCLUDED.quiet_hours_enabled, quiet_start_minute = EXCLUDED.quiet_start_minute,
    quiet_end_minute = EXCLUDED.quiet_end_minute, updated_at = CURRENT_TIMESTAMP
""",
                    environment,
                    subject,
                    input.soundEnabled(),
                    input.quietHoursEnabled(),
                    input.quietStartMinute(),
                    input.quietEndMinute());
            return settings(environment, subject);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAlertPreferences.class,
                    "save(String,UUID,Input)");
        }
    }

    /**
     * Subscribes to customer alert preferences data and returns the {@code SubscriptionResult}
     * result.
     *
     * <p>Reads {@code customer_push_subscriptions}, {@code verified_customer_sessions}.
     *
     * <p>Writes {@code OF}, {@code customer_push_subscriptions}.
     *
     * @param environment the environment supplied to this method
     * @param subject the subject supplied to this method
     * @param token the token supplied to this method
     * @param input the input supplied to this method
     * @return the {@code SubscriptionResult} result
     * @throws ResponseStatusException when the method rejects the request with {@code Invalid
     *     browser subscription.}; {@code Maximum five browsers per account.}; {@code Push is not
     *     configured. Your inbox remains available.}; {@code This browser is registered to another
     *     account. Disable its old subscription first.}
     */
    @Transactional
    public SubscriptionResult subscribe(
            String environment, UUID subject, String token, SubscriptionInput input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerAlertPreferences.class,
                        "subscribe(String,UUID,String,SubscriptionInput)");
        try {
            if (!transport.configured())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Push is not configured. Your inbox remains available.");
            if (input == null
                    || !WebPushTransport.validEndpoint(input.endpoint())
                    || !WebPushTransport.validRecipientKeys(input.publicKey(), input.authSecret()))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid browser subscription.");
            lockSubject(environment, subject);
            Long session =
                    jdbc.query(
                            """
SELECT id FROM verified_customer_sessions WHERE environment = ? AND verified_subject_id = ?
  AND token_digest = ? AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP FOR SHARE
""",
                            rs -> rs.next() ? rs.getLong(1) : null,
                            environment,
                            subject,
                            digest(token));
            if (session == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            String endpointHash = HexFormat.of().formatHex(digest(input.endpoint()));
            var existing =
                    jdbc.query(
                            """
SELECT s.id, s.subject_id, s.revoked_at IS NOT NULL OR v.revoked_at IS NOT NULL OR v.expires_at <= CURRENT_TIMESTAMP AS inactive, s.session_id, s.public_key, s.auth_secret
FROM customer_push_subscriptions s JOIN verified_customer_sessions v ON v.id = s.session_id
WHERE s.environment = ? AND s.endpoint_hash = ? FOR UPDATE OF s
""",
                            (rs, row) ->
                                    new Existing(
                                            (UUID) rs.getObject(1),
                                            (UUID) rs.getObject(2),
                                            rs.getBoolean(3),
                                            rs.getLong(4),
                                            rs.getString(5),
                                            rs.getString(6)),
                            environment,
                            endpointHash);
            if (!existing.isEmpty()
                    && !existing.getFirst().subject().equals(subject)
                    && !existing.getFirst().inactive())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This browser is registered to another account. Disable its old"
                                + " subscription first.");
            Long count =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM customer_push_subscriptions s JOIN"
                                + " verified_customer_sessions v ON v.id = s.session_id WHERE"
                                + " s.environment = ? AND s.subject_id = ? AND s.revoked_at IS NULL"
                                + " AND v.revoked_at IS NULL AND v.expires_at > CURRENT_TIMESTAMP"
                                + " AND s.endpoint_hash <> ?",
                            Long.class,
                            environment,
                            subject,
                            endpointHash);
            if (count != null && count >= 5)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Maximum five browsers per account.");
            if (!existing.isEmpty()
                    && !existing.getFirst().inactive()
                    && existing.getFirst().subject().equals(subject)
                    && existing.getFirst().sessionId() == session
                    && existing.getFirst().publicKey().equals(input.publicKey())
                    && existing.getFirst().auth().equals(input.authSecret()))
                return new SubscriptionResult(existing.getFirst().id());
            if (!existing.isEmpty()) {
                var id = existing.getFirst().id();
                // Rotate identity by creating a new subscription ID; old deliveries cannot target a
                // new owner.
                jdbc.update(
                        "UPDATE customer_push_subscriptions SET revoked_at = CURRENT_TIMESTAMP"
                                + " WHERE id = ?",
                        id);
                jdbc.update(
                        "UPDATE customer_push_subscriptions SET endpoint_hash = ? WHERE id = ?",
                        "retired:" + id,
                        id);
            }
            UUID id = UUID.randomUUID();
            jdbc.update(
                    """
INSERT INTO customer_push_subscriptions(id, environment, subject_id, session_id, endpoint,
    endpoint_hash, public_key, auth_secret) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
""",
                    id,
                    environment,
                    subject,
                    session,
                    input.endpoint(),
                    endpointHash,
                    input.publicKey(),
                    input.authSecret());
            return new SubscriptionResult(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAlertPreferences.class,
                    "subscribe(String,UUID,String,SubscriptionInput)");
        }
    }

    /**
     * Unsubscribes from customer alert preferences data.
     *
     * <p>Writes {@code customer_push_subscriptions}.
     *
     * @param environment the environment supplied to this method
     * @param subject the subject supplied to this method
     * @param id the id supplied to this method
     */
    @Transactional
    public void unsubscribe(String environment, UUID subject, UUID id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAlertPreferences.class, "unsubscribe(String,UUID,UUID)");
        try {
            if (jdbc.update(
                            "UPDATE customer_push_subscriptions SET revoked_at ="
                                    + " COALESCE(revoked_at, CURRENT_TIMESTAMP) WHERE id = ? AND"
                                    + " environment = ? AND subject_id = ?",
                            id,
                            environment,
                            subject)
                    == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAlertPreferences.class,
                    "unsubscribe(String,UUID,UUID)");
        }
    }

    /**
     * Locks subject.
     *
     * @param environment the environment
     * @param subject the subject
     */
    private void lockSubject(String environment, UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAlertPreferences.class, "lockSubject(String,UUID)");
        try {
            if (jdbc.query(
                            "SELECT 1 FROM verified_customer_subjects WHERE environment = ? AND id"
                                    + " = ? FOR UPDATE",
                            (rs, row) -> rs.getInt(1),
                            environment,
                            subject)
                    .isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAlertPreferences.class,
                    "lockSubject(String,UUID)");
        }
    }

    /**
     * Hashes the UTF-8 value with SHA-256, treating null as an empty byte sequence.
     *
     * @param value the value supplied to this method
     * @return the {@code byte[]} result
     * @throws IllegalStateException when the method rejects the request with {@code Hash
     *     unavailable}
     */
    static byte[] digest(String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerAlertPreferences.class, "digest(String)");
        try {
            try {
                return MessageDigest.getInstance("SHA-256")
                        .digest(
                                value == null
                                        ? new byte[0]
                                        : value.getBytes(StandardCharsets.UTF_8));
            } catch (Exception unavailable) {
                throw new IllegalStateException("Hash unavailable");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerAlertPreferences.class, "digest(String)");
        }
    }

    /**
     * Returns quiet information for customer alert preferences.
     *
     * @param enabled the enabled supplied to this method
     * @param start the start supplied to this method
     * @param end the end supplied to this method
     * @param now the now supplied to this method
     * @return the {@code boolean} result
     */
    public static boolean quiet(boolean enabled, int start, int end, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerAlertPreferences.class, "quiet(boolean,int,int,Instant)");
        try {
            if (!enabled) return false;
            var time = now.atZone(ZoneId.of("Asia/Kolkata"));
            int minute = time.getHour() * 60 + time.getMinute();
            return start < end ? minute >= start && minute < end : minute >= start || minute < end;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerAlertPreferences.class,
                    "quiet(boolean,int,int,Instant)");
        }
    }

    /**
     * Immutable existing data contract.
     *
     * @param id the id
     * @param subject the subject
     * @param inactive the inactive
     * @param sessionId the session id
     * @param publicKey the public key
     * @param auth the auth
     */
    private record Existing(
            UUID id,
            UUID subject,
            boolean inactive,
            long sessionId,
            String publicKey,
            String auth) {}
}
