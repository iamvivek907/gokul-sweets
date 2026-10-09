package com.gokulsweets.restaurant.config;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/** HTTP endpoints for storefront features operations. */
@RestController
@RequiredArgsConstructor
public class StorefrontFeaturesController {

    private final EnhancementProperties properties;

    private final Clock inventoryClock;

    /**
     * Handles {@code GET /api/storefront/features} for storefront features.
     *
     * @return the {@code Features} result
     */
    @GetMapping("/api/storefront/features")
    public Features features() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StorefrontFeaturesController.class, "features()");
        try {
            return new Features(
                    properties.isSmartAvailability(),
                    properties.isSmartAvailability() && properties.isSmartPickupSelection(),
                    properties.isInventoryAutomationV2(),
                    properties.isCustomerHomeV2(),
                    properties.isHomepageCampaigns(),
                    properties.isPreHomeIntentGateway(),
                    properties.isContextualStorefrontV2(),
                    properties.isControlledCampaignPublishing(),
                    properties.isBranchExperience(),
                    properties.isOccasionEnquiries() && properties.isCustomerOtpIdentity(),
                    properties.isOccasionEnquiries()
                            && properties.isOccasionPayments()
                            && properties.isCustomerOtpIdentity(),
                    properties.isAccessibleOrderingV2(),
                    properties.isFuturisticStorefrontV2(),
                    properties.isCheckoutExperienceV2()
                            && properties.isSmartAvailability()
                            && properties.isSmartPickupSelection()
                            && properties.isAuthoritativePickupCommitment()
                            && properties.isPersistentPickupContext()
                            && properties.isCartSwitchPreview()
                            && properties.isInPlaceBranchSwitch()
                            && properties.isAcceptedCheckoutQuote()
                            && properties.isAccessibleOrderingV2()
                            && properties.isPaymentPollingV2(),
                    properties.isPersistentPickupContext(),
                    properties.isCartSwitchPreview(),
                    properties.isPersistentPickupContext()
                            && properties.isCartSwitchPreview()
                            && properties.isInPlaceBranchSwitch(),
                    properties.isSmartAvailability()
                            && properties.isAuthoritativePickupCommitment(),
                    properties.isAcceptedCheckoutQuote(),
                    properties.isTruthfulOrderTracking(),
                    properties.isPaidCartRecovery(),
                    properties.isPaymentPollingV2(),
                    properties.isDeliveryLocalityCheck()
                            && properties.isCustomerConsentControls()
                            && properties.isCustomerOtpIdentity(),
                    properties.isDeliveryZones()
                            && properties.isDeliveryLocalityCheck()
                            && properties.isCustomerConsentControls()
                            && properties.isCustomerOtpIdentity(),
                    properties.isDeliveryCapacity()
                            && properties.isDeliveryZones()
                            && properties.isDeliveryLocalityCheck()
                            && properties.isCustomerConsentControls()
                            && properties.isCustomerOtpIdentity(),
                    properties.isDeliveryAddressBoundaries()
                            && properties.isDeliveryCapacity()
                            && properties.isDeliveryZones()
                            && properties.isDeliveryLocalityCheck()
                            && properties.isCustomerConsentControls()
                            && properties.isCustomerOtpIdentity(),
                    properties.deliveryCheckoutReady(),
                    properties.isCustomerAccountHub() && properties.isCustomerOtpIdentity(),
                    properties.isNotificationInbox() && properties.isCustomerOtpIdentity(),
                    properties.isNotificationAlerts()
                            && properties.isNotificationInbox()
                            && properties.isCustomerOtpIdentity(),
                    properties.rewardsReady(),
                    properties.isBrandCareers(),
                    properties.isPickupAddOns(),
                    properties.isSimplifiedCheckout(),
                    properties.isBilingualStorefront(),
                    properties.isAdminPreparationBoard(),
                    properties.getFutureOrderingDays(),
                    LocalDate.now(inventoryClock));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StorefrontFeaturesController.class, "features()");
        }
    }

    /**
     * Immutable features data contract.
     *
     * @param smartAvailability the smart availability
     * @param smartPickupSelection the smart pickup selection
     * @param inventoryAutomationV2 the inventory automation v2
     * @param customerHomeV2 the customer home v2
     * @param homepageCampaigns the homepage campaigns
     * @param preHomeIntentGateway the pre home intent gateway
     * @param contextualStorefrontV2 the contextual storefront v2
     * @param controlledCampaignPublishing the controlled campaign publishing
     * @param branchExperience the branch experience
     * @param occasionEnquiries the occasion enquiries
     * @param occasionPayments the occasion payments
     * @param accessibleOrderingV2 the accessible ordering v2
     * @param futuristicStorefrontV2 the futuristic storefront v2
     * @param checkoutExperienceV2 the checkout experience v2
     * @param persistentPickupContext the persistent pickup context
     * @param cartSwitchPreview the cart switch preview
     * @param inPlaceBranchSwitch the in place branch switch
     * @param authoritativePickupCommitment the authoritative pickup commitment
     * @param acceptedCheckoutQuote the accepted checkout quote
     * @param truthfulOrderTracking the truthful order tracking
     * @param paidCartRecovery the paid cart recovery
     * @param paymentPollingV2 the payment polling v2
     * @param deliveryLocalityCheck the delivery locality check
     * @param deliveryZones the delivery zones
     * @param deliveryCapacity the delivery capacity
     * @param deliveryAddressBoundaries the delivery address boundaries
     * @param deliveryCheckout the delivery checkout
     * @param customerAccountHub the customer account hub
     * @param notificationInbox the notification inbox
     * @param notificationAlerts the notification alerts
     * @param gokulRewards the gokul rewards
     * @param brandCareers the brand careers
     * @param pickupAddOns the pickup add ons
     * @param simplifiedCheckout the simplified checkout
     * @param bilingualStorefront the bilingual storefront
     * @param adminPreparationBoard the admin preparation board
     * @param futureOrderingDays the future ordering days
     * @param today the today
     */
    public record Features(
            boolean smartAvailability,
            boolean smartPickupSelection,
            boolean inventoryAutomationV2,
            boolean customerHomeV2,
            boolean homepageCampaigns,
            boolean preHomeIntentGateway,
            boolean contextualStorefrontV2,
            boolean controlledCampaignPublishing,
            boolean branchExperience,
            boolean occasionEnquiries,
            boolean occasionPayments,
            boolean accessibleOrderingV2,
            boolean futuristicStorefrontV2,
            boolean checkoutExperienceV2,
            boolean persistentPickupContext,
            boolean cartSwitchPreview,
            boolean inPlaceBranchSwitch,
            boolean authoritativePickupCommitment,
            boolean acceptedCheckoutQuote,
            boolean truthfulOrderTracking,
            boolean paidCartRecovery,
            boolean paymentPollingV2,
            boolean deliveryLocalityCheck,
            boolean deliveryZones,
            boolean deliveryCapacity,
            boolean deliveryAddressBoundaries,
            boolean deliveryCheckout,
            boolean customerAccountHub,
            boolean notificationInbox,
            boolean notificationAlerts,
            boolean gokulRewards,
            boolean brandCareers,
            boolean pickupAddOns,
            boolean simplifiedCheckout,
            boolean bilingualStorefront,
            boolean adminPreparationBoard,
            int futureOrderingDays,
            LocalDate today) {}
}
