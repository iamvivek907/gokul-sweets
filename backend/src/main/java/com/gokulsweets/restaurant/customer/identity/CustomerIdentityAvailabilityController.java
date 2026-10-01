package com.gokulsweets.restaurant.customer.identity;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Publishes readiness without exposing either server-side credential. */
@RestController
@RequiredArgsConstructor
public class CustomerIdentityAvailabilityController {
    private final EnhancementProperties features;
    private final Environment settings;

    @GetMapping("/api/storefront/customer-identity")
    public ResponseEntity<Map<String, Boolean>> availability() {
        boolean enabled = features.isCustomerOtpIdentity()
                && settings.getProperty("gokul.environment-isolation.enabled", Boolean.class, false)
                && settings.getProperty("gokul.web.environment-cors-enabled", Boolean.class, false)
                && ("DEV".equals(settings.getProperty("gokul.environment-isolation.environment"))
                    || "PROD".equals(settings.getProperty("gokul.environment-isolation.environment")))
                && !settings.getProperty("gokul.msg91.server-authkey", "").isBlank()
                && settings.getProperty("gokul.identity.rate-limit-key", "").length() >= 32;
        enabled = enabled && settings.getProperty("gokul.identity.provider-abuse-controls-verified", Boolean.class, false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("enabled", enabled, "guestCheckoutEnabled", settings.getProperty("gokul.checkout.guest-enabled", Boolean.class, true)));
    }
}
