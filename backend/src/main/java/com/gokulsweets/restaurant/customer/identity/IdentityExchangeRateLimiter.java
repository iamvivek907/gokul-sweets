package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Database-backed limits shared by every instance; identifiers never enter the table. */
@Service
@RequiredArgsConstructor
public class IdentityExchangeRateLimiter {

    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private final JdbcTemplate jdbc;

    private final Environment settings;

    /**
     * Checks source.
     *
     * @param environment the environment
     * @param sourceAddress the source address
     * @param now the now
     */
    public void checkSource(ConsentEnvironment environment, String sourceAddress, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        IdentityExchangeRateLimiter.class,
                        "checkSource(ConsentEnvironment,String,Instant)");
        try {
            // Only a trusted server-derived remote address may be passed here. Never use a
            // browser-provided identifier or untrusted X-Forwarded-For as the sole limit.
            if (sourceAddress == null || !sourceAddress.matches("[0-9a-fA-F:.]{3,45}")) {
                throw new IllegalArgumentException("A server-observed IP address is required");
            }
            check(environment, "SOURCE", sourceAddress, now, Duration.ofMinutes(15), 5);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityExchangeRateLimiter.class,
                    "checkSource(ConsentEnvironment,String,Instant)");
        }
    }

    /**
     * Checks verified phone.
     *
     * @param environment the environment
     * @param phone the phone
     * @param now the now
     */
    public void checkVerifiedPhone(ConsentEnvironment environment, String phone, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        IdentityExchangeRateLimiter.class,
                        "checkVerifiedPhone(ConsentEnvironment,String,Instant)");
        try {
            if (phone == null || !phone.matches("\\+91[6-9][0-9]{9}")) {
                throw new IllegalArgumentException("A provider-verified mobile is required");
            }
            check(environment, "PHONE", phone, now, Duration.ofHours(1), 5);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityExchangeRateLimiter.class,
                    "checkVerifiedPhone(ConsentEnvironment,String,Instant)");
        }
    }

    /**
     * Checks start source.
     *
     * @param environment the environment
     * @param sourceAddress the source address
     * @param now the now
     */
    public void checkStartSource(
            ConsentEnvironment environment, String sourceAddress, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        IdentityExchangeRateLimiter.class,
                        "checkStartSource(ConsentEnvironment,String,Instant)");
        try {
            if (sourceAddress == null || !sourceAddress.matches("[0-9a-fA-F:.]{3,45}")) {
                throw new IllegalArgumentException("A server-observed IP address is required");
            }
            check(environment, "START_SOURCE", sourceAddress, now, Duration.ofMinutes(15), 5);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityExchangeRateLimiter.class,
                    "checkStartSource(ConsentEnvironment,String,Instant)");
        }
    }

    /**
     * Checks device.
     *
     * @param environment the environment
     * @param deviceToken the device token
     * @param now the now
     */
    public void checkDevice(ConsentEnvironment environment, String deviceToken, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        IdentityExchangeRateLimiter.class,
                        "checkDevice(ConsentEnvironment,String,Instant)");
        try {
            if (deviceToken == null || !deviceToken.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("A server-issued device cookie is required");
            }
            check(environment, "DEVICE", deviceToken, now, Duration.ofHours(1), 5);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityExchangeRateLimiter.class,
                    "checkDevice(ConsentEnvironment,String,Instant)");
        }
    }

    /**
     * Checks start device.
     *
     * @param environment the environment
     * @param deviceToken the device token
     * @param now the now
     */
    public void checkStartDevice(ConsentEnvironment environment, String deviceToken, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        IdentityExchangeRateLimiter.class,
                        "checkStartDevice(ConsentEnvironment,String,Instant)");
        try {
            if (deviceToken == null || !deviceToken.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("A server-issued device cookie is required");
            }
            check(environment, "START_DEVICE", deviceToken, now, Duration.ofHours(1), 5);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityExchangeRateLimiter.class,
                    "checkStartDevice(ConsentEnvironment,String,Instant)");
        }
    }

    /**
     * Checks the operation.
     *
     * @param environment the environment
     * @param scope the scope
     * @param value the value
     * @param now the now
     * @param window the window
     * @param maximum the maximum
     */
    private void check(
            ConsentEnvironment environment,
            String scope,
            String value,
            Instant now,
            Duration window,
            int maximum) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        IdentityExchangeRateLimiter.class,
                        "check(ConsentEnvironment,String,String,Instant,Duration,int)");
        try {
            Objects.requireNonNull(environment);
            Objects.requireNonNull(now);
            var key = settings.getProperty("gokul.identity.rate-limit-key", "");
            if (key.length() < 32) {
                throw new IllegalStateException("Identity exchange rate limit is unavailable");
            }
            var count =
                    jdbc.queryForObject(
                            """
INSERT INTO identity_exchange_limits
    (environment, scope, key_digest, window_start, attempts)
VALUES (?, ?, ?, ?, 1)
ON CONFLICT (environment, scope, key_digest)
DO UPDATE SET
    attempts = CASE WHEN identity_exchange_limits.window_start <= ?
        THEN 1 ELSE identity_exchange_limits.attempts + 1 END,
    window_start = CASE WHEN identity_exchange_limits.window_start <= ?
        THEN EXCLUDED.window_start ELSE identity_exchange_limits.window_start END
RETURNING attempts
""",
                            Integer.class,
                            environment.name(),
                            scope,
                            digest(key, scope, value),
                            Timestamp.from(now),
                            Timestamp.from(now.minus(window)),
                            Timestamp.from(now.minus(window)));
            if (count == null || count > maximum) {
                throw new Limited();
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityExchangeRateLimiter.class,
                    "check(ConsentEnvironment,String,String,Instant,Duration,int)");
        }
    }

    /**
     * Digests the operation.
     *
     * @param key the key
     * @param scope the scope
     * @param value the value
     * @return the digest result
     */
    private static byte[] digest(String key, String scope, String value) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        IdentityExchangeRateLimiter.class, "digest(String,String,String)");
        try {
            try {
                var mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                mac.update(scope.getBytes(StandardCharsets.US_ASCII));
                mac.update((byte) 0);
                return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new IllegalStateException("Identity rate limit hashing unavailable", e);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityExchangeRateLimiter.class,
                    "digest(String,String,String)");
        }
    }

    /** Removes expired windows. */
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void removeExpiredWindows() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(IdentityExchangeRateLimiter.class, "removeExpiredWindows()");
        try {
            if (dedicatedImportWorker) return;
            jdbc.update(
                    "DELETE FROM identity_exchange_limits WHERE window_start < ?",
                    Timestamp.from(Instant.now().minus(Duration.ofHours(2))));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityExchangeRateLimiter.class,
                    "removeExpiredWindows()");
        }
    }

    /** Backend limited contract and implementation. */
    public static class Limited extends RuntimeException {

        /** Creates a limited instance. */
        public Limited() {
            super("Identity verification is temporarily limited");
        }
    }
}
