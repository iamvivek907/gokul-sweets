package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/** Server-minted opaque device token; a user-supplied random cookie is not accepted. */
@Service
@RequiredArgsConstructor
public class IdentityDeviceRegistry {

    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private static final Duration LIFETIME = Duration.ofDays(30);

    private final JdbcTemplate jdbc;

    private final SecureRandom random = new SecureRandom();

    /**
     * Issues identity device registry data and returns the {@code String} result.
     *
     * <p>Writes {@code identity_devices}.
     *
     * @param environment the environment supplied to this method
     * @param now the now supplied to this method
     * @return the value of {@code token}
     */
    public String issue(ConsentEnvironment environment, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        IdentityDeviceRegistry.class, "issue(ConsentEnvironment,Instant)");
        try {
            var bytes = new byte[32];
            random.nextBytes(bytes);
            var token = HexFormat.of().formatHex(bytes);
            jdbc.update(
                    """
                    INSERT INTO identity_devices(environment, token_digest, expires_at)
                    VALUES (?, ?, ?)
                    """,
                    environment.name(),
                    VerifiedCustomerSessionStore.digest(token),
                    Timestamp.from(now.plus(LIFETIME)));
            return token;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityDeviceRegistry.class,
                    "issue(ConsentEnvironment,Instant)");
        }
    }

    /**
     * Returns recognized information for identity device registry.
     *
     * <p>Reads {@code identity_devices}.
     *
     * @param environment the environment supplied to this method
     * @param token the token supplied to this method
     * @param now the now supplied to this method
     * @return the {@code boolean} result
     */
    public boolean recognized(ConsentEnvironment environment, String token, Instant now) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        IdentityDeviceRegistry.class,
                        "recognized(ConsentEnvironment,String,Instant)");
        try {
            if (token == null || !token.matches("[0-9a-f]{64}")) return false;
            return Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            """
                            SELECT EXISTS (SELECT 1 FROM identity_devices
                                WHERE environment = ? AND token_digest = ? AND expires_at > ?)
                            """,
                            Boolean.class,
                            environment.name(),
                            VerifiedCustomerSessionStore.digest(token),
                            Timestamp.from(now)));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    IdentityDeviceRegistry.class,
                    "recognized(ConsentEnvironment,String,Instant)");
        }
    }

    /** Removes expired. */
    @Scheduled(cron = "0 15 * * * *", zone = "UTC")
    public void removeExpired() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(IdentityDeviceRegistry.class, "removeExpired()");
        try {
            if (dedicatedImportWorker) return;
            jdbc.update(
                    "DELETE FROM identity_devices WHERE expires_at <= ?",
                    Timestamp.from(Instant.now()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, IdentityDeviceRegistry.class, "removeExpired()");
        }
    }
}
