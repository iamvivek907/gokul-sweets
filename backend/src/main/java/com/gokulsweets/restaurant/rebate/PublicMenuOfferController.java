package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Public promotion terms only. Cart eligibility is checked by the authenticated preview. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/menu/offers")
public class PublicMenuOfferController {
    private final RebateEligibilityService eligibility;
    private final EnhancementProperties flags;

    @GetMapping
    public List<RebateEligibilityService.PublicOffer> offers(@RequestParam long branchId) {
        return flags.isPickupAddOns() ? eligibility.publicOffers(branchId) : List.of();
    }
}
