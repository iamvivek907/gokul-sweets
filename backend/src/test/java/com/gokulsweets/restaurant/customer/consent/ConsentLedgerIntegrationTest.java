package com.gokulsweets.restaurant.customer.consent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ConsentLedgerIntegrationTest {
    @Autowired ConsentLedger ledger;

    @Test
    void defaultIsDeniedAndWithdrawalOverridesGrantOnlyForMatchingPurposeAndEnvironment() {
        var subject = UUID.randomUUID();
        assertThat(ledger.current(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING).granted())
                .isFalse();

        var grant = ledger.record(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING,
                "2026-09", true);
        assertThat(grant.granted()).isTrue();
        assertThat(grant.recordedAt()).isNotNull();
        assertThat(ledger.current(ConsentEnvironment.PROD, subject, ConsentPurpose.MARKETING).granted())
                .isFalse();
        assertThat(ledger.current(ConsentEnvironment.DEV, subject, ConsentPurpose.OCCASION_REMINDERS).granted())
                .isFalse();

        ledger.record(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING,
                "2026-09", false);
        assertThat(ledger.current(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING).granted())
                .isFalse();
    }

    @Test
    void rejectsUnverifiedSubjectAndInvalidPolicyVersionBeforeWriting() {
        assertThatThrownBy(() -> ledger.record(ConsentEnvironment.DEV, null,
                ConsentPurpose.MARKETING, "2026-09", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ledger.record(ConsentEnvironment.DEV, UUID.randomUUID(),
                ConsentPurpose.MARKETING, "", true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
