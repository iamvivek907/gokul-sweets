package com.gokulsweets.restaurant.customer.identity;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpChallengePolicyTest {
    private final OtpChallengePolicy policy = new OtpChallengePolicy(new SecureRandom(), new byte[32]);
    private final Instant now = Instant.parse("2026-09-26T16:00:00Z");

    @Test
    void correctCodeIsSingleUseAndDoesNotExposeItInLogs() {
        var issued = policy.issue("+919876543210", now);
        assertThat(issued.code()).matches("[0-9]{6}");
        assertThat(issued.toString()).doesNotContain(issued.code());
        var verified = policy.verify(issued.challenge(), "+919876543210", issued.code(), now);
        assertThat(verified.verified()).isTrue();
        assertThat(policy.verify(verified.challenge(), "+919876543210", issued.code(), now)
                .verified()).isFalse();
    }

    @Test
    void wrongPhoneOrCodeConsumesAttemptsAndExpiryFailsClosed() {
        var issued = policy.issue("+919876543210", now);
        var challenge = issued.challenge();
        for (int remaining = OtpChallengePolicy.MAX_ATTEMPTS - 1; remaining >= 0; remaining--) {
            var attempt = policy.verify(challenge, "+919876543211", issued.code(), now);
            assertThat(attempt.verified()).isFalse();
            challenge = attempt.challenge();
            assertThat(challenge.attemptsRemaining()).isEqualTo(remaining);
        }
        assertThat(policy.verify(challenge, "+919876543210", issued.code(), now).verified())
                .isFalse();
        assertThat(policy.verify(issued.challenge(), "+919876543210", issued.code(),
                now.plus(OtpChallengePolicy.EXPIRY)).verified()).isFalse();
        assertThat(issued.challenge().resendAfter()).isAfter(now);
        assertThat(policy.canResend(issued.challenge(), now.plusSeconds(59))).isFalse();
        assertThat(policy.canResend(issued.challenge(), now.plusSeconds(60))).isTrue();
    }

    @Test
    void rejectsUnnormalizedPhonesAndShortKeys() {
        assertThatThrownBy(() -> policy.issue("9876543210", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OtpChallengePolicy(new SecureRandom(), new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
