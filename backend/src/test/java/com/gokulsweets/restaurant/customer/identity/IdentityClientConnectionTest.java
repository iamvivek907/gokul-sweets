package com.gokulsweets.restaurant.customer.identity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityClientConnectionTest {
    @Test
    void directClientCannotSpoofProxyHeaders() {
        var resolver = new IdentityClientConnection(new MockEnvironment()
                .withProperty("gokul.identity.trusted-proxy-cidrs", "192.0.2.0/24"));
        var request = request("198.51.100.8", "203.0.113.7", "https");
        assertThat(resolver.resolve(request)).isEqualTo(
                new IdentityClientConnection.Connection("198.51.100.8", false));
    }

    @Test
    void trustedProxyUsesNearestUntrustedHopAndProxyTls() {
        var resolver = new IdentityClientConnection(new MockEnvironment()
                .withProperty("gokul.identity.trusted-proxy-cidrs", "192.0.2.0/24"));
        var request = request("192.0.2.10", "203.0.113.1, 198.51.100.7, 192.0.2.9", "http, https");
        assertThat(resolver.resolve(request)).isEqualTo(
                new IdentityClientConnection.Connection("198.51.100.7", true));
        request.removeHeader("X-Forwarded-Proto");
        request.addHeader("X-Forwarded-Proto", "http");
        assertThat(resolver.resolve(request).secure()).isFalse();
    }

    @Test
    void incompleteOrOverlyBroadProxyConfigurationFailsClosed() {
        var trusted = new IdentityClientConnection(new MockEnvironment()
                .withProperty("gokul.identity.trusted-proxy-cidrs", "192.0.2.0/24"));
        var request = request("192.0.2.10", "203.0.113.1", "https");
        request.removeHeader("X-Forwarded-For");
        assertThatThrownBy(() -> trusted.resolve(request)).isInstanceOf(IllegalStateException.class);

        var wildcard = new IdentityClientConnection(new MockEnvironment()
                .withProperty("gokul.identity.trusted-proxy-cidrs", "0.0.0.0/0"));
        assertThatThrownBy(() -> wildcard.resolve(request)).isInstanceOf(IllegalStateException.class);
    }

    private static MockHttpServletRequest request(String remote, String forwarded, String proto) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        request.addHeader("X-Forwarded-For", forwarded);
        request.addHeader("X-Forwarded-Proto", proto);
        return request;
    }
}
