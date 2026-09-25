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
    /** Show the selected pickup branch and slot across the customer ordering journey. */
    private boolean persistentPickupContext;
    /** Require a server-backed cart preview before changing a pickup branch or date. */
    private boolean cartSwitchPreview;
    /** Recheck the whole cart and chosen slot before atomic pickup and stock reservation. */
    private boolean authoritativePickupCommitment;
    /** Require a fresh signed, server-priced quote before creating or editing a checkout. */
    private boolean acceptedCheckoutQuote;
    /** Reconcile late provider success against the payment and reservation deadlines before fulfilment. */
    private boolean paymentReconciliationV2;
    /** Allow authorized branch staff to publish an honest revised readiness estimate. */
    private boolean truthfulOrderTracking;

    /** Aligns legacy LocalDateTime entity timestamps with Asia/Kolkata. */
    private boolean istTimeFixEnabled = true;

    @Min(1) @Max(60)
    private int futureOrderingDays = 30;
}
