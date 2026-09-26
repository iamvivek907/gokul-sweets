package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Database-backed limits shared by every instance; identifiers never enter the table. */
@Service
@RequiredArgsConstructor
public class IdentityExchangeRateLimiter {
    private final JdbcTemplate jdbc;
    private final Environment settings;

    public void checkSource(ConsentEnvironment environment, String sourceAddress, Instant now) {
        // Only a trusted server-derived remote address may be passed here. Never use a
        // browser-provided identifier or untrusted X-Forwarded-For as the sole limit.
        if (sourceAddress == null || !sourceAddress.matches("[0-9a-fA-F:.]{3,45}")) {
            throw new IllegalArgumentException("A server-observed IP address is required");
        }
        check(environment, "SOURCE", sourceAddress, now, Duration.ofMinutes(15), 5);
    }

    public void checkVerifiedPhone(ConsentEnvironment environment, String phone, Instant now) {
        if (phone == null || !phone.matches("\\+91[6-9][0-9]{9}")) {
            throw new IllegalArgumentException("A provider-verified mobile is required");
        }
        check(environment, "PHONE", phone, now, Duration.ofHours(1), 5);
    }

    private void check(ConsentEnvironment environment, String scope, String value,
                       Instant now, Duration window, int maximum) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(now);
        var key = settings.getProperty("gokul.identity.rate-limit-key", "");
        if (key.length() < 32) {
            throw new IllegalStateException("Identity exchange rate limit is unavailable");
        }
        var count = jdbc.queryForObject("""
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
                """, Integer.class, environment.name(), scope, digest(key, scope, value),
                Timestamp.from(now), Timestamp.from(now.minus(window)), Timestamp.from(now.minus(window)));
        if (count == null || count > maximum) {
            throw new Limited();
        }
    }

    private static byte[] digest(String key, String scope, String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(scope.getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) 0);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Identity rate limit hashing unavailable", e);
        }
    }

    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void removeExpiredWindows() {
        jdbc.update("DELETE FROM identity_exchange_limits WHERE window_start < ?",
                Timestamp.from(Instant.now().minus(Duration.ofHours(2))));
    }

    public static class Limited extends RuntimeException {
        public Limited() { super("Identity verification is temporarily limited"); }
    }
}
