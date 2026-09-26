package com.gokulsweets.restaurant.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class StorefrontFeaturesController {
    private final EnhancementProperties properties;
    private final Clock inventoryClock;

    @GetMapping("/api/storefront/features")
    public Features features() {
        return new Features(properties.isSmartAvailability(),
                properties.isSmartAvailability() && properties.isSmartPickupSelection(),
                properties.isInventoryAutomationV2(), properties.isCustomerHomeV2(),
                properties.isHomepageCampaigns(), properties.isPreHomeIntentGateway(),
                properties.isContextualStorefrontV2(), properties.isControlledCampaignPublishing(),
                properties.isAccessibleOrderingV2(), properties.isPersistentPickupContext(),
                properties.isCartSwitchPreview(),
                properties.isPersistentPickupContext() && properties.isCartSwitchPreview() && properties.isInPlaceBranchSwitch(),
                properties.isSmartAvailability() && properties.isAuthoritativePickupCommitment(),
                properties.isAcceptedCheckoutQuote(),
                properties.isTruthfulOrderTracking(),
                properties.isPaidCartRecovery(),
                properties.isPaymentPollingV2(),
                properties.getFutureOrderingDays(), LocalDate.now(inventoryClock));
    }

    public record Features(boolean smartAvailability, boolean smartPickupSelection,
                           boolean inventoryAutomationV2, boolean customerHomeV2,
                           boolean homepageCampaigns, boolean preHomeIntentGateway, boolean contextualStorefrontV2,
                           boolean controlledCampaignPublishing, boolean accessibleOrderingV2,
                           boolean persistentPickupContext, boolean cartSwitchPreview,
                           boolean inPlaceBranchSwitch,
                           boolean authoritativePickupCommitment,
                           boolean acceptedCheckoutQuote,
                           boolean truthfulOrderTracking,
                           boolean paidCartRecovery,
                           boolean paymentPollingV2,
                           int futureOrderingDays, LocalDate today) {}
}
