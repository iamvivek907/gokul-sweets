package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

/** Identity only: no order or historical consent ownership is granted here. */
@RestController
@RequestMapping("/api/customer/identity")
@RequiredArgsConstructor
public class CustomerIdentityController {
    private static final String COOKIE = "__Host-gokul-customer";
    private final VerifiedIdentityExchange exchange;
    private final VerifiedCustomerSessionStore sessions;
    private final EnhancementProperties features;
    private final Environment settings;
    private final WebCorsProperties cors;

    @PostMapping(value = "/exchange", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> exchange(@RequestBody ExchangeRequest payload,
                                                           HttpServletRequest request) {
        var environment = enabledEnvironment();
        requireTrustedMutation(request);
        if (payload == null || payload.accessToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identity proof required");
        }
        var issued = exchange.exchange(environment, request.getRemoteAddr(), payload.accessToken(), Instant.now());
        var cookie = ResponseCookie.from(COOKIE, issued.token())
                .httpOnly(true).secure(true).sameSite("Strict").path("/")
                .maxAge(Duration.between(Instant.now(), issued.expiresAt())).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("authenticated", true, "expiresAt", issued.expiresAt().toString()));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Boolean>> me(HttpServletRequest request) {
        var environment = enabledEnvironment();
        var token = cookie(request);
        boolean authenticated = sessions.subject(environment, token, Instant.now()).isPresent();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("authenticated", authenticated));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var environment = enabledEnvironment();
        requireTrustedMutation(request);
        sessions.revoke(environment, cookie(request), Instant.now());
        var expired = ResponseCookie.from(COOKIE, "")
                .httpOnly(true).secure(true).sameSite("Strict").path("/")
                .maxAge(Duration.ZERO).build();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, expired.toString()).build();
    }

    private ConsentEnvironment enabledEnvironment() {
        if (!features.isCustomerOtpIdentity()
                || !settings.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)
                || !settings.getProperty("gokul.web.environment-cors-enabled", Boolean.class, false)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return switch (settings.getProperty("gokul.environment-isolation.environment", "")) {
            case "DEV" -> ConsentEnvironment.DEV;
            case "PROD" -> ConsentEnvironment.PROD;
            default -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        };
    }

    private void requireTrustedMutation(HttpServletRequest request) {
        if (!request.isSecure() || !cors.effectiveAllowedOrigins(settings)
                .contains(request.getHeader(HttpHeaders.ORIGIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private static String cookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(value -> COOKIE.equals(value.getName()))
                .map(jakarta.servlet.http.Cookie::getValue).findFirst().orElse(null);
    }

    public record ExchangeRequest(String accessToken) { }

    @ExceptionHandler(IdentityExchangeRateLimiter.Limited.class)
    public ResponseEntity<Void> limited() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore()).build();
    }
}
