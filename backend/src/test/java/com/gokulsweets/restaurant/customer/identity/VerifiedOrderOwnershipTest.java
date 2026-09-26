package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerifiedOrderOwnershipTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EnhancementProperties features = new EnhancementProperties();
    private final MockEnvironment settings = new MockEnvironment()
            .withProperty("gokul.environment-isolation.enabled", "true")
            .withProperty("gokul.web.environment-cors-enabled", "true")
            .withProperty("gokul.environment-isolation.environment", "DEV");
    private final VerifiedOrderOwnership ownership = new VerifiedOrderOwnership(jdbc, features, settings);

    @Test
    void guestAndDisabledIdentityNeverAttemptOwnership() {
        ownership.bindNewOrder(3L, "9876543210", null);
        ownership.bindNewOrder(3L, "9876543210", "a".repeat(64));
        verifyNoInteractions(jdbc);
    }

    @Test
    void enabledIdentityChecksMatchingPhoneAndLiveSessionInsideDatabase() {
        features.setCustomerOtpIdentity(true);
        ownership.bindNewOrder(3L, "9876543210", "a".repeat(64));
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq("DEV"), any(byte[].class), any(),
                eq("+919876543210"), eq(3L), eq("DEV"), any());
        assertThat(sql.getValue()).contains("s.revoked_at IS NULL", "s.expires_at > ?",
                "v.verified_phone = ?", "FOR SHARE OF s, v");
    }

    @Test
    void lookupIsScopedToExactEnvironmentAndSubjectNotPhone() {
        var first = java.util.UUID.randomUUID();
        var second = java.util.UUID.randomUUID();
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("DEV"), eq(first))).thenReturn(java.util.List.of("GKS-FIRST"));
        assertThat(ownership.orderNumbers("DEV", first)).containsExactly("GKS-FIRST");
        assertThat(ownership.orderNumbers("PROD", second)).isEmpty();
        verify(jdbc).query(contains("ownership.verified_subject_id = ?"),
                any(org.springframework.jdbc.core.RowMapper.class), eq("DEV"), eq(first));
    }
}
