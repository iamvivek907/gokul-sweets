package com.gokulsweets.restaurant.customer.consent;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Evaluate optional processing immediately before sending/recording, never from browser state. */
@Service
@RequiredArgsConstructor
public class OptionalProcessingGate {
    private final ConsentLedger ledger;
    private final EnhancementProperties features;
    private final Environment settings;

    public boolean allows(ConsentEnvironment environment, UUID verifiedSubjectId, ConsentPurpose purpose) {
        if (environment == null || verifiedSubjectId == null || purpose == null
                || !features.isCustomerOtpIdentity() || !features.isCustomerConsentControls()
                || !settings.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)
                || !settings.getProperty("gokul.web.environment-cors-enabled", Boolean.class, false)
                || !settings.getProperty("gokul.identity.provider-abuse-controls-verified", Boolean.class, false)
                || !environment.name().equals(settings.getProperty("gokul.environment-isolation.environment", ""))) {
            return false;
        }
        String policy = settings.getProperty("gokul.consent.policy-version", "");
        if (!policy.matches("[A-Za-z0-9._-]{1,40}")) return false;
        var decision = ledger.current(environment, verifiedSubjectId, purpose);
        return decision.granted() && policy.equals(decision.policyVersion());
    }
}
