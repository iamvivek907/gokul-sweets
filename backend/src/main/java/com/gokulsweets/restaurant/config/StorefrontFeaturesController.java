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
                properties.isBranchExperience(),
                properties.isOccasionEnquiries() && properties.isCustomerOtpIdentity(),
                properties.isOccasionEnquiries() && properties.isOccasionPayments() && properties.isCustomerOtpIdentity(),
                properties.isAccessibleOrderingV2(), properties.isFuturisticStorefrontV2(),
                properties.isCheckoutExperienceV2() && properties.isSmartAvailability()
                        && properties.isSmartPickupSelection() && properties.isAuthoritativePickupCommitment()
                        && properties.isPersistentPickupContext() && properties.isCartSwitchPreview()
                        && properties.isInPlaceBranchSwitch() && properties.isAcceptedCheckoutQuote()
                        && properties.isAccessibleOrderingV2() && properties.isPaymentPollingV2(),
                properties.isPersistentPickupContext(),
                properties.isCartSwitchPreview(),
                properties.isPersistentPickupContext() && properties.isCartSwitchPreview() && properties.isInPlaceBranchSwitch(),
                properties.isSmartAvailability() && properties.isAuthoritativePickupCommitment(),
                properties.isAcceptedCheckoutQuote(),
                properties.isTruthfulOrderTracking(),
                properties.isPaidCartRecovery(),
                properties.isPaymentPollingV2(),
                properties.isDeliveryLocalityCheck() && properties.isCustomerConsentControls()
                        && properties.isCustomerOtpIdentity(),
                properties.isDeliveryZones() && properties.isDeliveryLocalityCheck()
                        && properties.isCustomerConsentControls() && properties.isCustomerOtpIdentity(),
                properties.isDeliveryCapacity() && properties.isDeliveryZones() && properties.isDeliveryLocalityCheck()
                        && properties.isCustomerConsentControls() && properties.isCustomerOtpIdentity(),
                properties.isDeliveryAddressBoundaries() && properties.isDeliveryCapacity()
                        && properties.isDeliveryZones() && properties.isDeliveryLocalityCheck()
                        && properties.isCustomerConsentControls() && properties.isCustomerOtpIdentity(),
                properties.deliveryCheckoutReady(),
                properties.isCustomerAccountHub() && properties.isCustomerOtpIdentity(),
                properties.isNotificationInbox() && properties.isCustomerOtpIdentity(),
                properties.isNotificationAlerts() && properties.isNotificationInbox() && properties.isCustomerOtpIdentity(),
                properties.isBrandCareers(), properties.isPickupAddOns(), properties.isSimplifiedCheckout(), properties.isBilingualStorefront(), properties.isAdminPreparationBoard(), properties.getFutureOrderingDays(), LocalDate.now(inventoryClock));
    }

    public record Features(boolean smartAvailability, boolean smartPickupSelection,
                           boolean inventoryAutomationV2, boolean customerHomeV2,
                           boolean homepageCampaigns, boolean preHomeIntentGateway, boolean contextualStorefrontV2,
                           boolean controlledCampaignPublishing, boolean branchExperience, boolean occasionEnquiries,
                           boolean occasionPayments,
                           boolean accessibleOrderingV2,
                           boolean futuristicStorefrontV2, boolean checkoutExperienceV2, boolean persistentPickupContext, boolean cartSwitchPreview,
                           boolean inPlaceBranchSwitch,
                           boolean authoritativePickupCommitment,
                           boolean acceptedCheckoutQuote,
                           boolean truthfulOrderTracking,
                           boolean paidCartRecovery,
                           boolean paymentPollingV2, boolean deliveryLocalityCheck, boolean deliveryZones,
                           boolean deliveryCapacity, boolean deliveryAddressBoundaries,
                           boolean deliveryCheckout, boolean customerAccountHub,
                           boolean notificationInbox, boolean notificationAlerts, boolean brandCareers, boolean pickupAddOns, boolean simplifiedCheckout, boolean bilingualStorefront, boolean adminPreparationBoard, int futureOrderingDays, LocalDate today) {}
}
