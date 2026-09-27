package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Treat the optional customer session cookie consistently for pickup and delivery checkout. */
@Component
@RequiredArgsConstructor
public class TrustedCheckoutIdentity {
    private final IdentityClientConnection clientConnection;
    private final WebCorsProperties cors;
    private final Environment settings;

    public String token(HttpServletRequest request) {
        if (request.getCookies() == null || !cors.effectiveAllowedOrigins(settings)
                .contains(request.getHeader(HttpHeaders.ORIGIN))) return null;
        try {
            if (!clientConnection.resolve(request).secure()) return null;
        } catch (IllegalStateException invalidProxy) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "__Host-gokul-customer".equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue).findFirst().orElse(null);
    }
}
