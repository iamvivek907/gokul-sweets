package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import com.gokulsweets.restaurant.customer.consent.ConsentLedger;
import com.gokulsweets.restaurant.customer.consent.ConsentPurpose;
import com.gokulsweets.restaurant.customer.consent.ConsentDecision;
import com.gokulsweets.restaurant.customer.consent.CustomerPrivacyRequests;
import com.gokulsweets.restaurant.customer.consent.PrivacyRequestKind;
import com.gokulsweets.restaurant.order.service.OrderQueryService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerIdentityControllerTest {
    private final VerifiedIdentityExchange exchange = mock(VerifiedIdentityExchange.class);
    private final VerifiedCustomerSessionStore sessions = mock(VerifiedCustomerSessionStore.class);
    private final VerifiedCustomerPhoneLookup subjects = mock(VerifiedCustomerPhoneLookup.class);
    private final VerifiedOrderOwnership ownership = mock(VerifiedOrderOwnership.class);
    private final OrderQueryService orders = mock(OrderQueryService.class);
    private final IdentityExchangeRateLimiter rateLimiter = mock(IdentityExchangeRateLimiter.class);
    private final IdentityDeviceRegistry devices = mock(IdentityDeviceRegistry.class);
    private final ConsentLedger consents = mock(ConsentLedger.class);
    private final CustomerPrivacyRequests privacyRequests = mock(CustomerPrivacyRequests.class);
    private final CustomerAccountHub accountHub = mock(CustomerAccountHub.class);
    private final com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox notifications = mock(com.gokulsweets.restaurant.customer.notification.CustomerNotificationInbox.class);
    private final EnhancementProperties features = new EnhancementProperties();
    private final MockEnvironment settings = new MockEnvironment()
            .withProperty("gokul.environment-isolation.enabled", "true")
            .withProperty("gokul.web.environment-cors-enabled", "true")
            .withProperty("gokul.identity.provider-abuse-controls-verified", "true")
            .withProperty("gokul.environment-isolation.environment", "DEV");
    private final CustomerIdentityController controller = new CustomerIdentityController(
            exchange, sessions, subjects, features, settings, new WebCorsProperties(), new IdentityClientConnection(settings),
            ownership, orders, rateLimiter, devices, consents, privacyRequests, accountHub, notifications);

    @Test
    void inboxRequiresEnabledFlagTrustedOriginAndCurrentSubjectForReadAndMutation() {
        features.setCustomerOtpIdentity(true);
        var trusted = request();
        trusted.setCookies(new Cookie("__Host-gokul-customer", "current-session"));
        var subject = UUID.randomUUID();
        when(sessions.subject(eq(ConsentEnvironment.DEV), eq("current-session"), any())).thenReturn(Optional.of(subject));
        assertThatThrownBy(() -> controller.notifications(null, trusted)).isInstanceOf(ResponseStatusException.class);
        verify(notifications, never()).page(anyString(), any(), any());
        when(notifications.enabled()).thenReturn(true);
        controller.notifications(null, trusted);
        verify(notifications).page("DEV", subject, null);
        controller.readNotification(42, trusted);
        verify(notifications).markRead("DEV", subject, 42);
        var foreign = request();
        foreign.setCookies(new Cookie("__Host-gokul-customer", "current-session"));
        foreign.removeHeader(HttpHeaders.ORIGIN);
        foreign.addHeader(HttpHeaders.ORIGIN, "https://untrusted.example");
        assertThatThrownBy(() -> controller.readNotification(43, foreign)).isInstanceOf(ResponseStatusException.class);
        verify(notifications, never()).markRead(anyString(), any(), eq(43L));
        var revoked = request();
        revoked.setCookies(new Cookie("__Host-gokul-customer", "revoked"));
        assertThatThrownBy(() -> controller.notificationPreferences(revoked)).isInstanceOf(ResponseStatusException.class);
        verify(notifications, never()).preferences(anyString(), any());
    }

    @Test
    void accountHubRequiresFlagTrustedOriginAndExactSessionSubject() {
        features.setCustomerOtpIdentity(true);
        var request = request();
        request.setCookies(new Cookie("__Host-gokul-customer", "session-token"));
        var subject = UUID.randomUUID();
        when(sessions.subject(eq(ConsentEnvironment.DEV), eq("session-token"), any()))
                .thenReturn(Optional.of(subject));
        assertThatThrownBy(() -> controller.account(request)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(accountHub);

        features.setCustomerAccountHub(true);
        var otherOrigin = request();
        otherOrigin.setCookies(new Cookie("__Host-gokul-customer", "session-token"));
        otherOrigin.removeHeader(HttpHeaders.ORIGIN);
        otherOrigin.addHeader(HttpHeaders.ORIGIN, "https://untrusted.example");
        assertThatThrownBy(() -> controller.account(otherOrigin)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(accountHub);

        controller.account(request);
        verify(accountHub).snapshot("DEV", subject);
        var revoked = request();
        revoked.setCookies(new Cookie("__Host-gokul-customer", "revoked"));
        assertThatThrownBy(() -> controller.deleteAddress(42, revoked)).isInstanceOf(ResponseStatusException.class);
        verify(accountHub, never()).deleteAddress(anyString(), any(), anyLong());

        controller.deleteAddress(42, request);
        verify(accountHub).deleteAddress("DEV", subject, 42);
    }

    @Test
    void privacyRequestsRequireVerifiedSubjectAndTrustedMutation() {
        features.setCustomerOtpIdentity(true);
        assertThatThrownBy(() -> controller.submitPrivacyRequest(PrivacyRequestKind.EXPORT, request()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(privacyRequests);

        features.setCustomerConsentControls(true);
        settings.withProperty("gokul.consent.policy-version", "2026-09");
        assertThatThrownBy(() -> controller.submitPrivacyRequest(PrivacyRequestKind.EXPORT, request()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(privacyRequests);

        var subject = UUID.randomUUID();
        var valid = request();
        valid.setCookies(new Cookie("__Host-gokul-customer", "current-session"));
        when(sessions.subject(eq(ConsentEnvironment.DEV), eq("current-session"), any()))
                .thenReturn(Optional.of(subject));
        controller.submitPrivacyRequest(PrivacyRequestKind.DELETION_REVIEW, valid);
        verify(privacyRequests).submit(ConsentEnvironment.DEV, subject, PrivacyRequestKind.DELETION_REVIEW);

        valid.setSecure(false);
        assertThatThrownBy(() -> controller.submitPrivacyRequest(PrivacyRequestKind.EXPORT, valid))
                .isInstanceOf(ResponseStatusException.class);
        verify(privacyRequests, never()).submit(ConsentEnvironment.DEV, subject, PrivacyRequestKind.EXPORT);
    }

    @Test
    void consentDefaultsOffAndRequiresVerifiedOwnerAndApprovedPolicy() {
        features.setCustomerOtpIdentity(true);
        assertThatThrownBy(() -> controller.consents(request()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(consents);

        features.setCustomerConsentControls(true);
        settings.withProperty("gokul.consent.policy-version", "2026-09");
        assertThatThrownBy(() -> controller.consents(request()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(consents);

        var subject = UUID.randomUUID();
        var authenticated = request();
        authenticated.setCookies(new Cookie("__Host-gokul-customer", "current-session"));
        when(sessions.subject(eq(ConsentEnvironment.DEV), eq("current-session"), any()))
                .thenReturn(Optional.of(subject));
        when(consents.current(eq(ConsentEnvironment.DEV), eq(subject), any()))
                .thenReturn(new ConsentDecision(false, "", null));
        assertThat(controller.consents(authenticated).getBody().get(ConsentPurpose.MARKETING).granted())
                .isFalse();
        when(consents.current(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING))
                .thenReturn(new ConsentDecision(true, "old-policy", Instant.now()));
        assertThat(controller.consents(authenticated).getBody().get(ConsentPurpose.MARKETING).granted())
                .isFalse();

        var choice = new CustomerIdentityController.ConsentChoice(true);
        controller.updateConsent(ConsentPurpose.MARKETING, choice, authenticated);
        verify(consents).record(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING, "2026-09", true);
        var wrongOrigin = request();
        wrongOrigin.setCookies(new Cookie("__Host-gokul-customer", "current-session"));
        wrongOrigin.removeHeader(HttpHeaders.ORIGIN);
        wrongOrigin.addHeader(HttpHeaders.ORIGIN, "https://attacker.example");
        assertThatThrownBy(() -> controller.updateConsent(ConsentPurpose.MARKETING, choice, wrongOrigin))
                .isInstanceOf(ResponseStatusException.class);
        verify(consents, times(1)).record(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void recoveredOrdersRequireCurrentSessionAndExactOwner() {
        features.setCustomerOtpIdentity(true);
        assertThatThrownBy(() -> controller.orders(request()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(ownership, orders);

        var subject = UUID.randomUUID();
        var authenticated = request();
        authenticated.setCookies(new Cookie("__Host-gokul-customer", "current-session"));
        when(sessions.subject(eq(ConsentEnvironment.DEV), eq("current-session"), any()))
                .thenReturn(Optional.of(subject));
        when(ownership.orderNumbers("DEV", subject)).thenReturn(java.util.List.of("GKS-ONE"));
        when(orders.getCustomerOrderHistory(java.util.List.of("GKS-ONE"))).thenReturn(java.util.List.of());
        assertThat(controller.orders(authenticated).getBody()).isEmpty();
        assertThatThrownBy(() -> controller.order("GKS-OTHER", authenticated))
                .isInstanceOf(ResponseStatusException.class);
        verify(ownership).owns("DEV", subject, "GKS-OTHER");
        verify(orders, never()).getCustomerOrder("GKS-OTHER");
    }

    @Test
    void disabledFeatureNeverConsultsProviderOrSessions() {
        assertThatThrownBy(() -> controller.exchange(new CustomerIdentityController.ExchangeRequest("proof"), request()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(exchange, sessions);
    }

    @Test
    void secureExactOriginSetsHostOnlyCookieWithoutBearerInBody() {
        features.setCustomerOtpIdentity(true);
        when(devices.recognized(eq(ConsentEnvironment.DEV), eq("a".repeat(64)), any())).thenReturn(true);
        when(exchange.exchange(eq(ConsentEnvironment.DEV), eq("192.0.2.1"), eq("a".repeat(64)), eq("proof"), isNull(), any()))
                .thenReturn(new VerifiedCustomerSessionStore.IssuedSession("secret-token", Instant.now().plusSeconds(3600)));
        var authenticated = request();
        authenticated.setCookies(new Cookie("__Host-gokul-device", "a".repeat(64)));
        var result = controller.exchange(new CustomerIdentityController.ExchangeRequest("proof"), authenticated);
        assertThat(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("__Host-gokul-customer=secret-token", "Secure", "HttpOnly", "SameSite=Strict", "Path=/")
                .doesNotContain("Domain=");
        assertThat(result.getBody().toString()).doesNotContain("secret-token");
        assertThat(result.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void startIssuesSecureDeviceCookieAndLimitsObservedSource() {
        features.setCustomerOtpIdentity(true);
        when(devices.issue(eq(ConsentEnvironment.DEV), any())).thenReturn("b".repeat(64));
        var started = controller.start(request());
        assertThat(started.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("__Host-gokul-device=", "Secure", "HttpOnly", "SameSite=Strict", "Path=/");
        verify(rateLimiter).checkStartSource(eq(ConsentEnvironment.DEV), eq("192.0.2.1"), any());
        verify(rateLimiter).checkStartDevice(eq(ConsentEnvironment.DEV), matches("[0-9a-f]{64}"), any());
    }

    @Test
    void rejectedOriginOrInsecureTransportCannotExchangeProof() {
        features.setCustomerOtpIdentity(true);
        var wrongOrigin = request();
        wrongOrigin.removeHeader(HttpHeaders.ORIGIN);
        wrongOrigin.addHeader(HttpHeaders.ORIGIN, "https://attacker.example");
        assertThatThrownBy(() -> controller.exchange(new CustomerIdentityController.ExchangeRequest("proof"), wrongOrigin))
                .isInstanceOf(ResponseStatusException.class);
        var insecure = request();
        insecure.setSecure(false);
        assertThatThrownBy(() -> controller.exchange(new CustomerIdentityController.ExchangeRequest("proof"), insecure))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(exchange);
    }

    @Test
    void statusReadsOnlyCookieAndLogoutRevokesAndClearsIt() {
        features.setCustomerOtpIdentity(true);
        var request = request();
        request.setCookies(new Cookie("__Host-gokul-customer", "session-token"));
        var subject = UUID.randomUUID();
        when(sessions.subject(eq(ConsentEnvironment.DEV), eq("session-token"), any()))
                .thenReturn(Optional.of(subject));
        when(subjects.verifiedPhone(ConsentEnvironment.DEV, subject)).thenReturn(Optional.of("+919876543210"));
        when(subjects.displayName(ConsentEnvironment.DEV, subject)).thenReturn(Optional.of("Vivek"));
        assertThat(controller.me(request).getBody())
                .containsEntry("authenticated", true).containsEntry("phone", "+919876543210")
                .containsEntry("name", "Vivek");
        controller.updateName(new CustomerIdentityController.NameRequest("Vivek C"), request);
        verify(subjects).updateDisplayName(ConsentEnvironment.DEV, subject, "Vivek C");
        var foreign = request();
        foreign.setCookies(new Cookie("__Host-gokul-customer", "session-token"));
        foreign.removeHeader(HttpHeaders.ORIGIN);
        foreign.addHeader(HttpHeaders.ORIGIN, "https://attacker.example");
        assertThatThrownBy(() -> controller.me(foreign)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.updateName(new CustomerIdentityController.NameRequest("Other"), foreign))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.updateName(new CustomerIdentityController.NameRequest(" "), request))
                .isInstanceOf(ResponseStatusException.class);
        verify(subjects, times(1)).updateDisplayName(any(), any(), any());
        var logout = controller.logout(request);
        verify(sessions).revoke(eq(ConsentEnvironment.DEV), eq("session-token"), any());
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0", "Path=/");
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setRemoteAddr("192.0.2.1");
        request.addHeader(HttpHeaders.ORIGIN, "https://dev.gokulsweets.in");
        return request;
    }
}
