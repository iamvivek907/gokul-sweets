package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test void guestOffRejectsMissingMalformedAndUnavailableIdentityBeforeAnyDatabaseWrite() {
        settings.setProperty("gokul.checkout.guest-enabled", "false");
        assertThatThrownBy(() -> ownership.requireCheckoutIdentity("9876543210", null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        features.setCustomerOtpIdentity(true);
        assertThatThrownBy(() -> ownership.requireCheckoutIdentity("9876543210", "invalid"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        settings.setProperty("gokul.environment-isolation.enabled", "false");
        assertThatThrownBy(() -> ownership.requireCheckoutIdentity("9876543210", "a".repeat(64)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void guestOffRequiresLivePhoneMatchedSessionAndSuccessfulOwnerBinding() {
        features.setCustomerOtpIdentity(true); settings.setProperty("gokul.checkout.guest-enabled", "false");
        assertThatThrownBy(() -> ownership.requireCheckoutIdentity("9876543210", "a".repeat(64)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        when(jdbc.queryForList(anyString(), eq(java.util.UUID.class), eq("DEV"), any(byte[].class), any(), eq("+919876543210")))
                .thenReturn(java.util.List.of(java.util.UUID.randomUUID()));
        assertThatThrownBy(() -> ownership.bindNewOrder(3L, "9876543210", "a".repeat(64)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(jdbc.update(anyString(), eq("DEV"), any(byte[].class), any(), eq("+919876543210"), eq(3L), eq("DEV"), any())).thenReturn(1);
        ownership.bindNewOrder(3L, "9876543210", "a".repeat(64));
        assertThatThrownBy(() -> ownership.requireCheckoutIdentity("9123456789", "a".repeat(64)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void guestOffIdempotentReplayRequiresTheCurrentOrdersOwner() {
        settings.setProperty("gokul.checkout.guest-enabled", "false");
        assertThatThrownBy(() -> ownership.requireCheckoutReplay(3L, "a".repeat(64)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(3L), eq("DEV"), any(byte[].class), any())).thenReturn(true);
        ownership.requireCheckoutReplay(3L, "a".repeat(64));
    }
}
