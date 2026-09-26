package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
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
    private static final Duration LIFETIME = Duration.ofDays(30);
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public String issue(ConsentEnvironment environment, Instant now) {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var token = HexFormat.of().formatHex(bytes);
        jdbc.update("""
                INSERT INTO identity_devices(environment, token_digest, expires_at)
                VALUES (?, ?, ?)
                """, environment.name(), VerifiedCustomerSessionStore.digest(token),
                Timestamp.from(now.plus(LIFETIME)));
        return token;
    }

    public boolean recognized(ConsentEnvironment environment, String token, Instant now) {
        if (token == null || !token.matches("[0-9a-f]{64}")) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM identity_devices
                    WHERE environment = ? AND token_digest = ? AND expires_at > ?)
                """, Boolean.class, environment.name(), VerifiedCustomerSessionStore.digest(token),
                Timestamp.from(now)));
    }

    @Scheduled(cron = "0 15 * * * *", zone = "UTC")
    public void removeExpired() {
        jdbc.update("DELETE FROM identity_devices WHERE expires_at <= ?", Timestamp.from(Instant.now()));
    }
}
