package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
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
    private final EnhancementProperties features = new EnhancementProperties();
    private final MockEnvironment settings = new MockEnvironment()
            .withProperty("gokul.environment-isolation.enabled", "true")
            .withProperty("gokul.web.environment-cors-enabled", "true")
            .withProperty("gokul.environment-isolation.environment", "DEV");
    private final CustomerIdentityController controller = new CustomerIdentityController(
            exchange, sessions, features, settings, new WebCorsProperties());

    @Test
    void disabledFeatureNeverConsultsProviderOrSessions() {
        assertThatThrownBy(() -> controller.exchange(new CustomerIdentityController.ExchangeRequest("proof"), request()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(exchange, sessions);
    }

    @Test
    void secureExactOriginSetsHostOnlyCookieWithoutBearerInBody() {
        features.setCustomerOtpIdentity(true);
        when(exchange.exchange(eq(ConsentEnvironment.DEV), eq("192.0.2.1"), eq("proof"), any()))
                .thenReturn(new VerifiedCustomerSessionStore.IssuedSession("secret-token", Instant.now().plusSeconds(3600)));
        var result = controller.exchange(new CustomerIdentityController.ExchangeRequest("proof"), request());
        assertThat(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("__Host-gokul-customer=secret-token", "Secure", "HttpOnly", "SameSite=Strict", "Path=/")
                .doesNotContain("Domain=");
        assertThat(result.getBody().toString()).doesNotContain("secret-token");
        assertThat(result.getHeaders().getCacheControl()).contains("no-store");
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
        when(sessions.subject(eq(ConsentEnvironment.DEV), eq("session-token"), any()))
                .thenReturn(Optional.of(UUID.randomUUID()));
        assertThat(controller.me(request).getBody()).containsEntry("authenticated", true);
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
