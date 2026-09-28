package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/** Internal exchange only. The customer API must supply environment from server configuration. */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerifiedIdentityExchange {
    private final Msg91WidgetProofVerifier verifier;
    private final VerifiedIdentityIssuance issuance;
    private final IdentityExchangeRateLimiter rateLimiter;

    public VerifiedCustomerSessionStore.IssuedSession exchange(
            ConsentEnvironment environment, String sourceAddress, String deviceToken,
            String accessToken, Instant now) {
        return exchange(environment, sourceAddress, deviceToken, accessToken, null, now);
    }

    public VerifiedCustomerSessionStore.IssuedSession exchange(
            ConsentEnvironment environment, String sourceAddress, String deviceToken,
            String accessToken, String previousSessionToken, Instant now) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(now);
        try {
            rateLimiter.checkSource(environment, sourceAddress, now);
            rateLimiter.checkDevice(environment, deviceToken, now);
        } catch (IllegalStateException e) {
            log.warn("Customer identity exchange unavailable: rate limit configuration");
            throw e;
        }
        // Network verification precedes the short database transaction.
        String verifiedPhone;
        try {
            verifiedPhone = verifier.verifiedPhone(accessToken);
        } catch (IllegalStateException e) {
            log.warn("Customer identity exchange unavailable: provider verification");
            throw e;
        }
        try {
            rateLimiter.checkVerifiedPhone(environment, verifiedPhone, now);
        } catch (IllegalStateException e) {
            log.warn("Customer identity exchange unavailable: verified phone rate limit configuration");
            throw e;
        }
        try {
            return issuance.issue(environment, accessToken, verifiedPhone, previousSessionToken, now);
        } catch (IllegalStateException e) {
            log.warn("Customer identity exchange unavailable: session issuance");
            throw e;
        }
    }
}
