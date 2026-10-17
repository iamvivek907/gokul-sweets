package com.gokulsweets.restaurant.rebate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.category.Category;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderItem;
import com.gokulsweets.restaurant.order.enums.*;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.product.Product;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

class RebateExclusionsTest {
    final RebateRepository rebates = mock(RebateRepository.class);
    final RebateSlabRepository slabs = mock(RebateSlabRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final RebateEligibilityService service =
            new RebateEligibilityService(
                    mock(OrderRepository.class),
                    payments,
                    rebates,
                    slabs,
                    mock(RebateCustomerRepository.class),
                    mock(RebateRedemptionRepository.class));

    BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    OrderItem line(long id, long category, String subtotal, String tax) {
        var c = new Category();
        c.setId(category);
        var p = new Product();
        p.setId(id);
        p.setCategory(c);
        var i = new OrderItem();
        i.setProduct(p);
        i.setQuantity(1);
        i.setLineTotal(n(subtotal).add(n(tax)));
        i.setTaxAmount(n(tax));
        return i;
    }

    Order basket() {
        var b = new Branch();
        b.setId(1L);
        var o = new Order();
        o.setBranch(b);
        o.setPickupType(PickupType.NORMAL);
        o.setFulfillmentType(FulfillmentType.PICKUP);
        o.setSubtotal(n("400"));
        o.setTaxAmount(n("20"));
        o.setPriorityCharge(BigDecimal.ZERO);
        o.setConvenienceFee(n("5"));
        o.getItems().addAll(List.of(line(10, 1, "300", "15"), line(20, 2, "100", "5")));
        return o;
    }

    Rebate offer(RebateType type) {
        var r = new Rebate();
        r.setId(1L);
        r.setCode("FOOD");
        r.setName("Food saving");
        r.setScope(RebateScope.GENERAL);
        r.setRebateType(type);
        r.setRebateValue(n("10"));
        r.setMaximumDiscountAmount(n("100"));
        r.setValidFrom(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusDays(1));
        r.setValidUntil(r.getValidFrom().plusDays(2));
        r.getExcludedCategoryIds().add(2L);
        when(rebates.findActivePublicCandidates(eq(1L), any())).thenReturn(List.of(r));
        when(rebates.findByCodeIgnoreCase("FOOD")).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void categoryExclusionKeepsAllGoodsPayable() {
        var o = basket();
        offer(RebateType.PERCENTAGE);
        var result = service.previewDraft(o).getFirst();
        assertThat(result.rebateAmount()).isEqualByComparingTo("30");
        assertThat(result.payableAfterRebate()).isEqualByComparingTo("395");
        assertThat(result.hasExclusions()).isTrue();
        assertThat(result.description()).contains("excluded");
        assertThat(service.findEligibleDraftRebate(o, "food").orElseThrow()).isEqualTo(result);
        verifyNoInteractions(payments);
    }

    @Test
    void excludedSpendTaxesAndFeesCannotUnlockMinimum() {
        var o = basket();
        var r = offer(RebateType.PERCENTAGE);
        r.setMinimumOrderAmount(n("301"));
        assertThat(service.previewDraft(o)).isEmpty();
        assertThat(service.findEligibleDraftRebate(o, "FOOD")).isEmpty();
        r.setMinimumOrderAmount(n("300"));
        assertThat(service.previewDraft(o)).hasSize(1);
    }

    @Test
    void categoryAndItemUnionCoversFutureCategoryProducts() {
        var o = basket();
        var r = offer(RebateType.FIXED_AMOUNT);
        r.getExcludedProductIds().addAll(Set.of(10L, 20L));
        assertThat(service.previewDraft(o)).isEmpty();
        r.getExcludedProductIds().remove(10L);
        o.getItems().add(line(21, 2, "500", "25"));
        assertThat(RebateSpendBasis.eligible(o, r)).isEqualByComparingTo("300");
        r.getExcludedCategoryIds().clear();
        assertThat(RebateSpendBasis.eligible(o, r)).isEqualByComparingTo("800");
    }

    @Test
    void fixedDiscountCannotConsumeExcludedGoods() {
        var o = basket();
        var r = offer(RebateType.FIXED_AMOUNT);
        r.setRebateValue(n("500"));
        r.setMaximumDiscountAmount(null);
        assertThat(service.previewDraft(o).getFirst().rebateAmount()).isEqualByComparingTo("300");
        assertThat(service.previewDraft(o).getFirst().payableAfterRebate())
                .isEqualByComparingTo("125");
    }

    @Test
    void loyaltySavingsAreAllocatedBeforeEligibility() {
        var o = basket();
        o.setLoyaltyEnrolled(true);
        o.setLoyaltyDiscount(n("40"));
        var r = offer(RebateType.PERCENTAGE);
        assertThat(RebateSpendBasis.eligible(o, r)).isEqualByComparingTo("270");
        var result = service.previewDraft(o).getFirst();
        assertThat(result.rebateAmount()).isEqualByComparingTo("27");
        assertThat(result.payableAfterRebate()).isEqualByComparingTo("358");
        r.setMinimumOrderAmount(n("280"));
        assertThat(service.previewDraft(o)).isEmpty();
    }

    @Test
    void eachOfferHasItsOwnBasisAndExistingOffersKeepPricing() {
        var o = basket();
        var food = offer(RebateType.PERCENTAGE);
        var all = new Rebate();
        all.setId(2L);
        all.setCode("ALL");
        all.setScope(RebateScope.GENERAL);
        all.setRebateType(RebateType.PERCENTAGE);
        all.setRebateValue(n("10"));
        all.setMaximumDiscountAmount(n("100"));
        when(rebates.findActivePublicCandidates(eq(1L), any())).thenReturn(List.of(food, all));
        assertThat(service.previewDraft(o))
                .extracting(x -> x.rebateAmount())
                .containsExactly(n("42.00"), n("30.00"));
    }

    @Test
    void slabTargetsAndExplicitCodeRevalidationRespectExclusions() {
        var o = basket();
        var r = offer(RebateType.SLAB);
        var slab = new RebateSlab();
        slab.setMinimumOrderAmount(n("350"));
        slab.setRebateAmount(n("20"));
        when(slabs.findByRebateIdOrderByMinimumOrderAmountAsc(1L)).thenReturn(List.of(slab));
        assertThat(service.previewDraft(o)).isEmpty();
        var target = service.previewSpendTargets(o, List.of()).getFirst();
        assertThat(target.amountNeededForNextSlab()).isEqualByComparingTo("50");
        assertThat(target.hasExclusions()).isTrue();
        r.getExcludedCategoryIds().clear();
        assertThat(service.findEligibleDraftRebate(o, "FOOD")).isPresent();
        r.getExcludedProductIds().add(10L);
        assertThat(service.findEligibleDraftRebate(o, "FOOD")).isEmpty();
    }

    @Test
    void weightedLinesUsePricedSubtotalAndCannotGoNegative() {
        var o = basket();
        var r = offer(RebateType.PERCENTAGE);
        o.getItems().getFirst().setWeightGrams(250);
        o.getItems().getFirst().setLineTotal(n("78.75"));
        o.getItems().getFirst().setTaxAmount(n("3.75"));
        assertThat(RebateSpendBasis.eligible(o, r)).isEqualByComparingTo("75");
        o.setLoyaltyEnrolled(true);
        o.setLoyaltyDiscount(n("9999"));
        assertThat(service.previewDraft(o)).isEmpty();
    }

    @Test
    void changedExclusionsInvalidateAnUnpaidSavedDiscountBeforePayment() {
        var o = basket();
        var r = offer(RebateType.PERCENTAGE);
        o.setId(9L);
        o.setOrderNumber("EXCLUSION-PAYMENT");
        o.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        o.setRebate(r);
        o.setRebateCode("FOOD");
        o.setRebateDiscountAmount(n("30"));
        o.setTotalAmount(n("395"));
        var application =
                new RebateApplicationService(
                        mock(OrderRepository.class), rebates, payments, service);
        application.revalidateAppliedRebateBeforePayment(o);
        r.getExcludedProductIds().add(10L);
        assertThatThrownBy(() -> application.revalidateAppliedRebateBeforePayment(o))
                .hasMessageContaining("review the updated order total");
        assertThat(o.getRebate()).isNull();
        assertThat(o.getTotalAmount()).isEqualByComparingTo("425");
    }
}
