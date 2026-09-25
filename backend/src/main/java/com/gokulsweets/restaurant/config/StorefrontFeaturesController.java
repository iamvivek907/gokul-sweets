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
                properties.isHomepageCampaigns(), properties.isPersistentPickupContext(),
                properties.isCartSwitchPreview(),
                properties.getFutureOrderingDays(), LocalDate.now(inventoryClock));
    }

    public record Features(boolean smartAvailability, boolean smartPickupSelection,
                           boolean inventoryAutomationV2, boolean customerHomeV2,
                           boolean homepageCampaigns, boolean persistentPickupContext, boolean cartSwitchPreview,
                           int futureOrderingDays, LocalDate today) {}
}
