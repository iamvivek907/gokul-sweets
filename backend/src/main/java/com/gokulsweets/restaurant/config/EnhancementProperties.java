package com.gokulsweets.restaurant.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "gokul.features")
@Validated
@Getter
@Setter
public class EnhancementProperties {
    private boolean smartAvailability;
    private boolean smartPickupSelection;
    private boolean inventoryAutomationV2;
    private boolean customerHomeV2;
    private boolean homepageCampaigns;

    /** Aligns legacy LocalDateTime entity timestamps with Asia/Kolkata. */
    private boolean istTimeFixEnabled = true;

    @Min(1) @Max(60)
    private int futureOrderingDays = 30;
}
