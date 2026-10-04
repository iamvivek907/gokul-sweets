package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.common.security.WebCorsProperties;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;

/** Optional compatibility boundary for legacy order-number endpoints. */
@Service
@RequiredArgsConstructor
public class VerifiedOrderAccess {
    private final EnhancementProperties features;
    private final Environment settings;
    private final WebCorsProperties cors;
    private final IdentityClientConnection connection;
    private final VerifiedCustomerSessionStore sessions;
    private final VerifiedOrderOwnership ownership;
    private final JdbcTemplate jdbc;

    public void requireOrder(String orderNumber, HttpServletRequest request) {
        if (!mayRead(orderNumber, request)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public void requirePayment(Long paymentId, HttpServletRequest request) {
        if (!active()) return;
        var orderNumber = jdbc.query("""
                SELECT o.order_number FROM payments p JOIN orders o ON o.id = p.order_id
                WHERE p.id = ?
                """, rs -> rs.next() ? rs.getString(1) : null, paymentId);
        if (orderNumber == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        requireOrder(orderNumber, request);
    }

    public boolean mayRead(String orderNumber, HttpServletRequest request) {
        if (!active()) return true;
        if (orderNumber == null || orderNumber.isBlank()) return false;
        orderNumber = orderNumber.trim().toUpperCase(java.util.Locale.ROOT);
        // An order owned in the other environment must not fall back to guest access.
        boolean verified = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM verified_order_ownership own
                    JOIN orders o ON o.id = own.order_id WHERE o.order_number = ?)
                """, Boolean.class, orderNumber));
        if (!verified) return true;
        String configured = settings.getProperty("gokul.environment-isolation.environment", "");
        ConsentEnvironment environment;
        try { environment = ConsentEnvironment.valueOf(configured); }
        catch (IllegalArgumentException invalid) { return false; }
        try {
            if (!connection.resolve(request).secure()
                    || !cors.effectiveAllowedOrigins(settings).contains(request.getHeader(HttpHeaders.ORIGIN))) {
                return false;
            }
        } catch (IllegalStateException invalidConnection) {
            return false;
        }
        String token = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(cookie -> "__Host-gokul-customer".equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue).findFirst().orElse(null);
        String normalizedOrderNumber = orderNumber;
        return sessions.subject(environment, token, Instant.now())
                .map(subject -> ownership.owns(environment.name(), subject, normalizedOrderNumber))
                .orElse(false);
    }

    /** Pickup secrets require an actual owner session even when legacy order reads are public. */
    public void requirePickupCode(String orderNumber,HttpServletRequest request) {requireOwner(orderNumber,request);}

    /** Financial changes never use the optional public/legacy read boundary. */
    public void requireOwner(String orderNumber,HttpServletRequest request) {
        if(orderNumber==null||orderNumber.isBlank())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        final String reference=orderNumber.trim().toUpperCase(java.util.Locale.ROOT);
        String configured=settings.getProperty("gokul.environment-isolation.environment","");
        ConsentEnvironment environment;
        try {environment=ConsentEnvironment.valueOf(configured);}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
        try {
            if(!connection.resolve(request).secure()||!cors.effectiveAllowedOrigins(settings).contains(request.getHeader(HttpHeaders.ORIGIN)))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }catch(IllegalStateException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
        String token=request.getCookies()==null?null:Arrays.stream(request.getCookies()).filter(c->"__Host-gokul-customer".equals(c.getName())).map(jakarta.servlet.http.Cookie::getValue).findFirst().orElse(null);
        boolean owns=sessions.subject(environment,token,Instant.now()).map(subject->ownership.owns(environment.name(),subject,reference)).orElse(false);
        if(!owns)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private boolean active() {
        return features.isCustomerOtpIdentity()
                && settings.getProperty("gokul.identity.protect-legacy-routes", Boolean.class, false)
                && settings.getProperty("gokul.identity.provider-abuse-controls-verified", Boolean.class, false)
                && settings.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)
                && settings.getProperty("gokul.web.environment-cors-enabled", Boolean.class, false);
    }
}
