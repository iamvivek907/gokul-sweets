package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.customer.consent.ConsentEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class VerifiedIdentityExchangeTest {
    @Test
    void failedProviderProofNeverReachesIssuance() {
        var verifier = mock(Msg91WidgetProofVerifier.class);
        var issuance = mock(VerifiedIdentityIssuance.class);
        var limiter = mock(IdentityExchangeRateLimiter.class);
        when(verifier.verifiedPhone("bad-proof")).thenThrow(new IllegalStateException("provider rejected"));
        var exchange = new VerifiedIdentityExchange(verifier, issuance, limiter);
        assertThatThrownBy(() -> exchange.exchange(ConsentEnvironment.DEV, "127.0.0.1", "bad-proof", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(issuance);
    }

    @Test
    void sourceLimitBlocksProviderRequestAndIssuance() {
        var verifier = mock(Msg91WidgetProofVerifier.class);
        var issuance = mock(VerifiedIdentityIssuance.class);
        var limiter = mock(IdentityExchangeRateLimiter.class);
        var now = Instant.now();
        doThrow(new IllegalStateException("limited")).when(limiter)
                .checkSource(ConsentEnvironment.DEV, "127.0.0.1", now);
        var exchange = new VerifiedIdentityExchange(verifier, issuance, limiter);
        assertThatThrownBy(() -> exchange.exchange(ConsentEnvironment.DEV, "127.0.0.1", "proof", now))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(verifier, issuance);
    }
}
