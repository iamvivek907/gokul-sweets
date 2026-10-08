package com.gokulsweets.restaurant.customer.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;
import java.util.UUID;

class VerifiedOrderAccessTest {
    @Test
    void pickupCodeRequiresOwnerSessionEvenWhenLegacyReadsArePublic() {
        var flags = new EnhancementProperties();
        var settings =
                new MockEnvironment()
                        .withProperty("gokul.environment-isolation.environment", "DEV");
        var connection = mock(IdentityClientConnection.class);
        doThrow(new IllegalStateException("No customer connection"))
                .when(connection)
                .resolve(any());
        var sessions = mock(VerifiedCustomerSessionStore.class);
        var ownership = mock(VerifiedOrderOwnership.class);
        var cors = new WebCorsProperties();
        var access =
                new VerifiedOrderAccess(
                        flags,
                        settings,
                        cors,
                        connection,
                        sessions,
                        ownership,
                        mock(JdbcTemplate.class));
        var request = new MockHttpServletRequest();
        assertThat(access.mayRead("KNOWN-ORDER", request)).isTrue();
        assertThatThrownBy(() -> access.requirePickupCode("KNOWN-ORDER", request))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(ownership);
    }

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final VerifiedCustomerSessionStore sessions = mock(VerifiedCustomerSessionStore.class);
    private final VerifiedOrderOwnership ownership = mock(VerifiedOrderOwnership.class);
    private final EnhancementProperties features = new EnhancementProperties();
    private final MockEnvironment settings =
            new MockEnvironment()
                    .withProperty("gokul.identity.protect-legacy-routes", "true")
                    .withProperty("gokul.identity.provider-abuse-controls-verified", "true")
                    .withProperty("gokul.environment-isolation.enabled", "true")
                    .withProperty("gokul.web.environment-cors-enabled", "true")
                    .withProperty("gokul.environment-isolation.environment", "DEV");
    private final VerifiedOrderAccess access =
            new VerifiedOrderAccess(
                    features,
                    settings,
                    new WebCorsProperties(),
                    new IdentityClientConnection(settings),
                    sessions,
                    ownership,
                    jdbc);

    @Test
    void flagOffPreservesGuestOrderNumberCapability() {
        assertThat(access.mayRead("GKS-ORDER", request())).isTrue();
        verifyNoInteractions(jdbc, sessions, ownership);
    }

    @Test
    void ownedOrderRequiresCurrentSessionAndCannotUseDifferentSubject() {
        features.setCustomerOtpIdentity(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("GKS-ORDER"))).thenReturn(true);
        assertThat(access.mayRead("gks-order", request())).isFalse();

        var request = request();
        request.setCookies(new Cookie("__Host-gokul-customer", "session"));
        var subject = UUID.randomUUID();
        when(sessions.subject(eq(ConsentEnvironment.DEV), eq("session"), any()))
                .thenReturn(Optional.of(subject));
        when(ownership.owns("DEV", subject, "GKS-ORDER")).thenReturn(true);
        assertThat(access.mayRead("gks-order", request)).isTrue();
        request.setSecure(false);
        assertThat(access.mayRead("GKS-ORDER", request)).isFalse();
    }

    @Test
    void guestOrderStillWorksAndForeignOwnedOrderIsHidden() {
        features.setCustomerOtpIdentity(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("GKS-GUEST")))
                .thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("GKS-FOREIGN")))
                .thenReturn(true);
        assertThat(access.mayRead("GKS-GUEST", request())).isTrue();
        assertThatThrownBy(() -> access.requireOrder("GKS-FOREIGN", request()))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setRemoteAddr("192.0.2.5");
        request.addHeader("Origin", "https://dev.gokulsweets.in");
        return request;
    }
}
