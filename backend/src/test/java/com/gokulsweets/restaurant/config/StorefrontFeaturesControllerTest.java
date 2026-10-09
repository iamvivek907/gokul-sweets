package com.gokulsweets.restaurant.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

class StorefrontFeaturesControllerTest {
    private final Clock clock =
            Clock.fixed(Instant.parse("2026-09-25T18:31:00Z"), ZoneId.of("Asia/Kolkata"));

    @Test
    void rewardsRequireTheirOwnSwitchIdentityAndAcceptedQuotes() {
        var properties = new EnhancementProperties();
        var controller = new StorefrontFeaturesController(properties, clock);
        properties.setGokulRewards(true);
        assertThat(controller.features().gokulRewards()).isFalse();
        properties.setCustomerOtpIdentity(true);
        assertThat(controller.features().gokulRewards()).isFalse();
        properties.setAcceptedCheckoutQuote(true);
        assertThat(controller.features().gokulRewards()).isTrue();
        properties.setGokulRewards(false);
        assertThat(controller.features().gokulRewards()).isFalse();
        properties.setGokulRewards(true);
        properties.setCustomerOtpIdentity(false);
        assertThat(controller.features().gokulRewards()).isFalse();
    }

    @Test
    void optionalAlertsRequireInboxAndIdentityAndNeverEnableThem() {
        var properties = new EnhancementProperties();
        properties.setNotificationAlerts(true);
        var controller = new StorefrontFeaturesController(properties, clock);
        assertThat(controller.features().notificationAlerts()).isFalse();
        properties.setNotificationInbox(true);
        assertThat(controller.features().notificationAlerts()).isFalse();
        properties.setCustomerOtpIdentity(true);
        assertThat(controller.features().notificationAlerts()).isTrue();
        properties.setNotificationInbox(false);
        assertThat(controller.features().notificationAlerts()).isFalse();
    }

    @Test
    void notificationInboxRequiresItsOwnSwitchAndVerifiedIdentity() {
        var properties = new EnhancementProperties();
        var controller = new StorefrontFeaturesController(properties, clock);
        assertThat(controller.features().notificationInbox()).isFalse();
        properties.setNotificationInbox(true);
        assertThat(controller.features().notificationInbox()).isFalse();
        properties.setCustomerOtpIdentity(true);
        assertThat(controller.features().notificationInbox()).isTrue();
        properties.setNotificationInbox(false);
        assertThat(controller.features().notificationInbox()).isFalse();
    }

    @Test
    void occasionPaymentsRequireEnquiriesAndVerifiedIdentity() {
        var properties = new EnhancementProperties();
        var controller = new StorefrontFeaturesController(properties, clock);
        properties.setOccasionPayments(true);
        assertThat(controller.features().occasionPayments()).isFalse();
        properties.setOccasionEnquiries(true);
        assertThat(controller.features().occasionPayments()).isFalse();
        properties.setCustomerOtpIdentity(true);
        assertThat(controller.features().occasionPayments()).isTrue();
        properties.setOccasionPayments(false);
        assertThat(controller.features().occasionPayments()).isFalse();
    }

    @Test
    void deliveryLocationCheckRequiresPrivacyAndIdentityGates() {
        var properties = new EnhancementProperties();
        var controller = new StorefrontFeaturesController(properties, clock);
        assertThat(controller.features().deliveryLocalityCheck()).isFalse();
        properties.setDeliveryLocalityCheck(true);
        assertThat(controller.features().deliveryLocalityCheck()).isFalse();
        properties.setCustomerConsentControls(true);
        assertThat(controller.features().deliveryLocalityCheck()).isFalse();
        properties.setCustomerOtpIdentity(true);
        assertThat(controller.features().deliveryLocalityCheck()).isTrue();
        properties.setCustomerConsentControls(false);
        assertThat(controller.features().deliveryLocalityCheck()).isFalse();
    }

    @Test
    void deliveryZonesRequireLocalityPrivacyAndIdentity() {
        var properties = new EnhancementProperties();
        var controller = new StorefrontFeaturesController(properties, clock);
        properties.setDeliveryZones(true);
        assertThat(controller.features().deliveryZones()).isFalse();
        properties.setDeliveryLocalityCheck(true);
        properties.setCustomerConsentControls(true);
        assertThat(controller.features().deliveryZones()).isFalse();
        properties.setCustomerOtpIdentity(true);
        assertThat(controller.features().deliveryZones()).isTrue();
        properties.setDeliveryLocalityCheck(false);
        assertThat(controller.features().deliveryZones()).isFalse();
    }

    @Test
    void deliveryCapacityRequiresZoneAndPrivacyPrerequisites() {
        var properties = new EnhancementProperties();
        properties.setDeliveryCapacity(true);
        var controller = new StorefrontFeaturesController(properties, clock);
        assertThat(controller.features().deliveryCapacity()).isFalse();
        properties.setDeliveryZones(true);
        properties.setDeliveryLocalityCheck(true);
        properties.setCustomerConsentControls(true);
        properties.setCustomerOtpIdentity(true);
        assertThat(controller.features().deliveryCapacity()).isTrue();
        properties.setDeliveryZones(false);
        assertThat(controller.features().deliveryCapacity()).isFalse();
    }

    @Test
    void deliveryAddressBoundariesRequireCapacityAndPrivacyGates() {
        var properties = new EnhancementProperties();
        properties.setDeliveryAddressBoundaries(true);
        var controller = new StorefrontFeaturesController(properties, clock);
        assertThat(controller.features().deliveryAddressBoundaries()).isFalse();
        properties.setDeliveryCapacity(true);
        properties.setDeliveryZones(true);
        properties.setDeliveryLocalityCheck(true);
        properties.setCustomerConsentControls(true);
        properties.setCustomerOtpIdentity(true);
        assertThat(controller.features().deliveryAddressBoundaries()).isTrue();
        properties.setCustomerConsentControls(false);
        assertThat(controller.features().deliveryAddressBoundaries()).isFalse();
    }

    @Test
    void contextDisplayDefaultsOffAndUsesIndiaBusinessDate() {
        var properties = new EnhancementProperties();
        var result = new StorefrontFeaturesController(properties, clock).features();
        assertThat(result.persistentPickupContext()).isFalse();
        assertThat(result.cartSwitchPreview()).isFalse();
        assertThat(result.paidCartRecovery()).isFalse();
        assertThat(result.paymentPollingV2()).isFalse();
        assertThat(result.inPlaceBranchSwitch()).isFalse();
        assertThat(result.authoritativePickupCommitment()).isFalse();
        assertThat(result.today()).isEqualTo(LocalDate.of(2026, 9, 26));
    }

    @Test
    void futuristicStorefrontIsOffByDefaultAndIndependentOfCheckout() {
        var properties = new EnhancementProperties();
        var controller = new StorefrontFeaturesController(properties, clock);
        assertThat(controller.features().futuristicStorefrontV2()).isFalse();
        properties.setFuturisticStorefrontV2(true);
        assertThat(controller.features().futuristicStorefrontV2()).isTrue();
        assertThat(controller.features().checkoutExperienceV2()).isFalse();
        properties.setFuturisticStorefrontV2(false);
        assertThat(controller.features().futuristicStorefrontV2()).isFalse();
    }

    @Test
    void checkoutExperienceRequiresAllCommitmentAndPaymentSafeguards() {
        var properties = new EnhancementProperties();
        properties.setCheckoutExperienceV2(true);
        var controller = new StorefrontFeaturesController(properties, clock);
        assertThat(controller.features().checkoutExperienceV2()).isFalse();

        properties.setSmartAvailability(true);
        properties.setSmartPickupSelection(true);
        properties.setAuthoritativePickupCommitment(true);
        properties.setPersistentPickupContext(true);
        properties.setCartSwitchPreview(true);
        properties.setInPlaceBranchSwitch(true);
        properties.setAcceptedCheckoutQuote(true);
        properties.setAccessibleOrderingV2(true);
        properties.setPaymentPollingV2(true);
        assertThat(controller.features().checkoutExperienceV2()).isTrue();

        properties.setAcceptedCheckoutQuote(false);
        assertThat(controller.features().checkoutExperienceV2()).isFalse();
        properties.setAcceptedCheckoutQuote(true);
        properties.setCheckoutExperienceV2(false);
        assertThat(controller.features().checkoutExperienceV2()).isFalse();
    }

    @Test
    void contextDisplayCanBeEnabledWithoutAvailabilityOrChangingExistingFlags() {
        var properties = new EnhancementProperties();
        properties.setPersistentPickupContext(true);
        var result = new StorefrontFeaturesController(properties, clock).features();
        assertThat(result.persistentPickupContext()).isTrue();
        assertThat(result.smartAvailability()).isFalse();
        assertThat(result.smartPickupSelection()).isFalse();
        assertThat(result.cartSwitchPreview()).isFalse();
        properties.setCartSwitchPreview(true);
        properties.setPaidCartRecovery(true);
        properties.setPaymentPollingV2(true);
        properties.setPreHomeIntentGateway(true);
        properties.setContextualStorefrontV2(true);
        properties.setControlledCampaignPublishing(true);
        properties.setAccessibleOrderingV2(true);
        properties.setInPlaceBranchSwitch(true);
        var previewEnabled = new StorefrontFeaturesController(properties, clock).features();
        assertThat(previewEnabled.cartSwitchPreview()).isTrue();
        assertThat(previewEnabled.paidCartRecovery()).isTrue();
        assertThat(previewEnabled.paymentPollingV2()).isTrue();
        assertThat(previewEnabled.preHomeIntentGateway()).isTrue();
        assertThat(previewEnabled.contextualStorefrontV2()).isTrue();
        assertThat(previewEnabled.controlledCampaignPublishing()).isTrue();
        assertThat(previewEnabled.accessibleOrderingV2()).isTrue();
        assertThat(previewEnabled.inPlaceBranchSwitch()).isTrue();
        assertThat(previewEnabled.smartAvailability()).isFalse();
        properties.setAuthoritativePickupCommitment(true);
        assertThat(
                        new StorefrontFeaturesController(properties, clock)
                                .features()
                                .authoritativePickupCommitment())
                .isFalse();
        properties.setSmartAvailability(true);
        assertThat(
                        new StorefrontFeaturesController(properties, clock)
                                .features()
                                .authoritativePickupCommitment())
                .isTrue();
    }
}
