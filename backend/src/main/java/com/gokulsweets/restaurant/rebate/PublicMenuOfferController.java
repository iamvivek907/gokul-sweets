package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

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

    /**
     * Offerses the operation.
     *
     * @param branchId the branch id
     * @return the offers result
     */
    @GetMapping
    public List<RebateEligibilityService.PublicOffer> offers(@RequestParam long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(PublicMenuOfferController.class, "offers(long)");
        try {
            return flags.isPickupAddOns() ? eligibility.publicOffers(branchId) : List.of();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, PublicMenuOfferController.class, "offers(long)");
        }
    }
}
