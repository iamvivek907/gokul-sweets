package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/** Internal exchange only. The customer API must supply environment from server configuration. */
@Service
@RequiredArgsConstructor
public class VerifiedIdentityExchange {
    private final Msg91WidgetProofVerifier verifier;
    private final VerifiedIdentityIssuance issuance;
    private final IdentityExchangeRateLimiter rateLimiter;

    public VerifiedCustomerSessionStore.IssuedSession exchange(
            ConsentEnvironment environment, String sourceAddress, String accessToken, Instant now) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(now);
        rateLimiter.checkSource(environment, sourceAddress, now);
        // Network verification precedes the short database transaction.
        String verifiedPhone = verifier.verifiedPhone(accessToken);
        rateLimiter.checkVerifiedPhone(environment, verifiedPhone, now);
        return issuance.issue(environment, accessToken, verifiedPhone, now);
    }
}
