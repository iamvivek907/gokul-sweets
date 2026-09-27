package com.gokulsweets.restaurant.customer.consent;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OptionalProcessingGateIntegrationTest {
    @Autowired ConsentLedger ledger;

    @Test
    void failsClosedAndRechecksWithdrawalPolicyAndEnvironmentOnEachUse() {
        var flags = new EnhancementProperties();
        var settings = new MockEnvironment()
                .withProperty("gokul.environment-isolation.enabled", "true")
                .withProperty("gokul.web.environment-cors-enabled", "true")
                .withProperty("gokul.identity.provider-abuse-controls-verified", "true")
                .withProperty("gokul.environment-isolation.environment", "DEV")
                .withProperty("gokul.consent.policy-version", "2026-09");
        var gate = new OptionalProcessingGate(ledger, flags, settings);
        var subject = UUID.randomUUID();

        assertThat(gate.allows(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING)).isFalse();
        ledger.record(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING, "2026-09", true);
        assertThat(gate.allows(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING)).isFalse();
        flags.setCustomerOtpIdentity(true);
        flags.setCustomerConsentControls(true);
        assertThat(gate.allows(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING)).isTrue();
        assertThat(gate.allows(ConsentEnvironment.DEV, subject, ConsentPurpose.OCCASION_REMINDERS)).isFalse();
        assertThat(gate.allows(ConsentEnvironment.PROD, subject, ConsentPurpose.MARKETING)).isFalse();
        assertThat(gate.allows(ConsentEnvironment.DEV, UUID.randomUUID(), ConsentPurpose.MARKETING)).isFalse();

        // A withdrawal made on another device is seen at the very next decision.
        ledger.record(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING, "2026-09", false);
        assertThat(gate.allows(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING)).isFalse();
        ledger.record(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING, "2026-09", true);
        settings.withProperty("gokul.consent.policy-version", "2026-10");
        assertThat(gate.allows(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING)).isFalse();
        settings.withProperty("gokul.environment-isolation.environment", "PROD");
        assertThat(gate.allows(ConsentEnvironment.DEV, subject, ConsentPurpose.MARKETING)).isFalse();
    }
}
