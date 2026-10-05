package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.identity.CustomerVisitPolicy;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicMenuOfferTest {
    final RebateRepository rebates = mock(RebateRepository.class);
    final RebateSlabRepository slabs = mock(RebateSlabRepository.class);
    final RebateRedemptionRepository redemptions = mock(RebateRedemptionRepository.class);
    final CustomerVisitPolicy visits = mock(CustomerVisitPolicy.class);
    final RebateEligibilityService service = new RebateEligibilityService(mock(OrderRepository.class),
            mock(PaymentRepository.class), rebates, slabs, mock(RebateCustomerRepository.class), redemptions);

    Rebate offer(long id, RebateScope scope) {
        var r = new Rebate(); r.setId(id); r.setCode("SAVE" + id); r.setName("Saving");
        r.setScope(scope); r.setRebateType(RebateType.FIXED_AMOUNT); r.setRebateValue(new BigDecimal("20"));
        r.setMinimumOrderAmount(new BigDecimal("200")); return r;
    }

    @Test void excludesPersonalizedVisitRestrictedAndExhaustedPromotions() {
        service.setVisitPolicy(visits);
        var general=offer(1,RebateScope.GENERAL); var personal=offer(2,RebateScope.CUSTOMER);
        var restricted=offer(3,RebateScope.GENERAL); var exhausted=offer(4,RebateScope.GENERAL); exhausted.setMaxTotalUses(5);
        when(rebates.findActivePublicCandidates(eq(7L),any())).thenReturn(List.of(general,personal,restricted,exhausted));
        when(visits.eligibleOffers(any(),any(),eq(CustomerVisitPolicy.SelectionMode.PREVIEW))).thenReturn(Set.of(1L,4L));
        when(redemptions.countByRebateId(4L)).thenReturn(5L);
        assertThat(service.publicOffers(7)).extracting(RebateEligibilityService.PublicOffer::code).containsExactly("SAVE1");
        verify(rebates).findActivePublicCandidates(eq(7L),any());
        verify(redemptions,never()).countByRebateIdAndCustomerPhone(any(),any());
    }

    @Test void listsConfiguredSlabsWithCapsInsteadOfClaimingACartSaving() {
        var r=offer(1,RebateScope.GENERAL); r.setRebateType(RebateType.SLAB);r.setMaximumDiscountAmount(new BigDecimal("25"));
        var slab=new RebateSlab();slab.setMinimumOrderAmount(new BigDecimal("300"));slab.setRebateAmount(new BigDecimal("40"));
        when(rebates.findActivePublicCandidates(eq(7L),any())).thenReturn(List.of(r));
        when(slabs.findByRebateIdOrderByMinimumOrderAmountAsc(1L)).thenReturn(List.of(slab));
        var result=service.publicOffers(7).getFirst();
        assertThat(result.tiers().getFirst().minimumOrderAmount()).isEqualByComparingTo("300");
        assertThat(result.tiers().getFirst().rebateAmount()).isEqualByComparingTo("25");
    }

    @Test void fixedAmountHeadlineHonoursTheDiscountCap() {
        var r=offer(1,RebateScope.GENERAL); r.setMaximumDiscountAmount(new BigDecimal("10"));
        when(rebates.findActivePublicCandidates(eq(7L),any())).thenReturn(List.of(r));
        assertThat(service.publicOffers(7).getFirst().rebateValue()).isEqualByComparingTo("10");
    }

    @Test void publishesExpiryAsAnUnambiguousInstantFromBusinessTime() {
        var r=offer(1,RebateScope.GENERAL);
        r.setValidUntil(java.time.LocalDateTime.of(2026,10,5,18,0));
        when(rebates.findActivePublicCandidates(eq(7L),any())).thenReturn(List.of(r));
        assertThat(service.publicOffers(7).getFirst().validUntil())
                .isEqualTo(java.time.Instant.parse("2026-10-05T12:30:00Z"));
    }

    @Test void coalescesTiersBelowTheOfferMinimumUsingCheckoutSlabOrder() {
        var r=offer(1,RebateScope.GENERAL); r.setRebateType(RebateType.SLAB);
        r.setMinimumOrderAmount(new BigDecimal("500"));
        var first=new RebateSlab();first.setMinimumOrderAmount(new BigDecimal("300"));first.setRebateAmount(new BigDecimal("60"));
        var second=new RebateSlab();second.setMinimumOrderAmount(new BigDecimal("400"));second.setRebateAmount(new BigDecimal("40"));
        when(rebates.findActivePublicCandidates(eq(7L),any())).thenReturn(List.of(r));
        when(slabs.findByRebateIdOrderByMinimumOrderAmountAsc(1L)).thenReturn(List.of(first,second));
        var tiers=service.publicOffers(7).getFirst().tiers();
        assertThat(tiers).hasSize(1);
        assertThat(tiers.getFirst().minimumOrderAmount()).isEqualByComparingTo("500");
        assertThat(tiers.getFirst().rebateAmount()).isEqualByComparingTo("40");
    }

    @Test void disabledFeatureDoesNotReadOffers() {
        var eligibility=mock(RebateEligibilityService.class);
        assertThat(new PublicMenuOfferController(eligibility,new EnhancementProperties()).offers(7)).isEmpty();
        verifyNoInteractions(eligibility);
    }
}
