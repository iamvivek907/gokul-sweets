package com.gokulsweets.restaurant.security;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StaffMfaServiceTest {
    @Test void knownRfcTotpCounterAcceptsSixDigitsAndRejectsOthers() {
        var service = new StaffMfaService(new EnhancementProperties(), mock(JdbcTemplate.class),
                Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC));
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertEquals(1L, (Long) ReflectionTestUtils.invokeMethod(service, "counter", secret, "287082"));
        assertEquals(-1L, (Long) ReflectionTestUtils.invokeMethod(service, "counter", secret, "000000"));
    }
    @Test void rolloutRequiresEncryptionKey() {
        var flags = new EnhancementProperties(); flags.setSecureStaffSessions(true);
        var service = new StaffMfaService(flags, mock(JdbcTemplate.class), Clock.systemUTC());
        assertThrows(IllegalStateException.class, service::validateKey);
    }
}
