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
        when(verifier.verifiedPhone("bad-proof")).thenThrow(new IllegalStateException("provider rejected"));
        var exchange = new VerifiedIdentityExchange(verifier, issuance);
        assertThatThrownBy(() -> exchange.exchange(ConsentEnvironment.DEV, "bad-proof", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(issuance);
    }
}
