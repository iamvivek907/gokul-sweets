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

    public VerifiedCustomerSessionStore.IssuedSession exchange(
            ConsentEnvironment environment, String accessToken, Instant now) {
        Objects.requireNonNull(environment);
        Objects.requireNonNull(now);
        // Network verification precedes the short database transaction.
        String verifiedPhone = verifier.verifiedPhone(accessToken);
        return issuance.issue(environment, accessToken, verifiedPhone, now);
    }
}
