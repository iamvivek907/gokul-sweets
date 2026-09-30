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
    /** SCRUM-105: published branch artwork and details; OFF until staff and media QA. */
    private boolean branchExperience;
    /** SCRUM-33: identify future pickup backed entirely by approved daily production. */
    private boolean plannedPickupProduction;
    /** SCRUM-34: future delivery needs approved daily production ready by the rider window. */
    private boolean plannedDeliveryProduction;
    /** SCRUM-35: reviewed occasion food enquiries; OFF until staff and customer QA. */
    private boolean occasionEnquiries;
    /** SCRUM-35: PhonePe deposit and balance settlement, independently OFF until DEV provider QA. */
    private boolean occasionPayments;
    /** Manager-approved bulk production, separate from daily retail stock. */
    private boolean occasionBulkProduction;
    /** SCRUM-28: accessible dialogs, mobile actions, motion and network recovery. */
    private boolean accessibleOrderingV2;
    /** Customer-facing site and navigation visual direction; never affects staff or admin pages. */
    private boolean futuristicStorefrontV2;
    /** SCRUM-95: approved checkout presentation; effective only with pickup, quote and payment safeguards. */
    private boolean checkoutExperienceV2;
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
    /** SCRUM-36: requires a verified SMS provider and customer session implementation before activation. */
    private boolean customerOtpIdentity;
    /** SCRUM-37: account hub and owner-scoped saved details. Requires verified identity. */
    private boolean customerAccountHub;
    /** SCRUM-39: durable verified-customer inbox; OFF until lifecycle and customer QA. */
    private boolean notificationInbox;
    /** SCRUM-40: optional browser push and user-controlled in-page sound. */
    private boolean notificationAlerts;
    private boolean staffOrderAlerts;
    /** SCRUM-49: verified customer optional-purpose controls; requires an approved policy version. */
    private boolean customerConsentControls;
    /** SCRUM-29: delivery locality intake; effective only alongside reviewed privacy controls. */
    private boolean deliveryLocalityCheck;
    /** SCRUM-30: manage delivery zones and return provisional coverage only. */
    private boolean deliveryZones;
    /** SCRUM-30: explicit rider capacity preview; never enables delivery checkout by itself. */
    private boolean deliveryCapacity;
    /** SCRUM-30: exact reviewed polygon/pin check; requires delivery-capacity preview. */
    private boolean deliveryAddressBoundaries;
    /** SCRUM-30: internal idempotent rider holds; not exposed to checkout until order lifecycle is ready. */
    private boolean deliveryRiderHolds;
    /** SCRUM-30: require a fresh signed delivery price before internal order creation. */
    private boolean deliveryAcceptedQuote;
    /** SCRUM-30: customer delivery order creation; requires signed quote and rider/inventory lifecycle. */
    private boolean deliveryCheckout;
    /** SCRUM-108 coordinated staff cookie migration; requires MFA encryption key and frontend release. */
    private boolean secureStaffSessions;
    /** SCRUM-31: reviewed, versioned delivery costs; OFF until finance approves inputs. */
    private boolean deliveryEconomics;
    /** SCRUM-32: supervised rider assignment and exception board, OFF during migration. */
    private boolean deliveryDispatchPilot;

    public boolean deliveryCheckoutReady() {
        return deliveryCheckout && deliveryAcceptedQuote && deliveryRiderHolds
                && deliveryAddressBoundaries && deliveryCapacity && deliveryZones
                && deliveryLocalityCheck && customerConsentControls && customerOtpIdentity;
    }

    /** Aligns legacy LocalDateTime entity timestamps with Asia/Kolkata. */
    private boolean istTimeFixEnabled = true;

    @Min(1) @Max(60)
    private int futureOrderingDays = 30;
}
