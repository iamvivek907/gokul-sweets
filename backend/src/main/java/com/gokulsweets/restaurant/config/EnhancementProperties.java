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
    /** SCRUM-25: show a quick first-visit choice before home; returning visitors continue to home. */
    private boolean preHomeIntentGateway;
    /** SCRUM-26: clarify branch menu, product units, availability and cart estimates. */
    private boolean contextualStorefrontV2;
    /** SCRUM-27: stage campaign edits and serve atomic, reversible published snapshots. */
    private boolean controlledCampaignPublishing;
    /** SCRUM-28: accessible dialogs, mobile actions, motion and network recovery. */
    private boolean accessibleOrderingV2;
    /** Show the selected pickup branch and slot across the customer ordering journey. */
    private boolean persistentPickupContext;
    /** Require a server-backed cart preview before changing a pickup branch or date. */
    private boolean cartSwitchPreview;
    /** Show the branch selector in checkout and actionable conflicts on the cart. */
    private boolean inPlaceBranchSwitch;
    /** Recheck the whole cart and chosen slot before atomic pickup and stock reservation. */
    private boolean authoritativePickupCommitment;
    /** Require a fresh signed, server-priced quote before creating or editing a checkout. */
    private boolean acceptedCheckoutQuote;
    /** Reconcile late provider success against the payment and reservation deadlines before fulfilment. */
    private boolean paymentReconciliationV2;
    /** Bound storefront payment-status polling and pause on provider throttling. */
    private boolean paymentPollingV2;
    /** Allow authorized branch staff to publish an honest revised readiness estimate. */
    private boolean truthfulOrderTracking;
    /** Remove only the matching pending checkout cart when server payment is PAID. */
    private boolean paidCartRecovery;

    /** Aligns legacy LocalDateTime entity timestamps with Asia/Kolkata. */
    private boolean istTimeFixEnabled = true;

    @Min(1) @Max(60)
    private int futureOrderingDays = 30;
}
