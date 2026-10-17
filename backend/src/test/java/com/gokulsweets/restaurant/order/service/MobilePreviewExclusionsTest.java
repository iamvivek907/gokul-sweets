package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.category.Category;
import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.customer.identity.VerifiedOrderOwnership;
import com.gokulsweets.restaurant.loyalty.LoyaltyService;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.model.*;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import com.gokulsweets.restaurant.rebate.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** Adapter regressions use real quote mapping and eligibility, not a hand-built rebate draft. */
class MobilePreviewExclusionsTest {
    final VerifiedOrderOwnership ownership = mock(VerifiedOrderOwnership.class);
    final LoyaltyService loyalty = mock(LoyaltyService.class);
    final OrderValidationService validation = mock(OrderValidationService.class);
    final OrderCalculationService calculation = mock(OrderCalculationService.class);
    final RebateRepository repository = mock(RebateRepository.class);
    final RebateSlabRepository slabs = mock(RebateSlabRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final RebateEligibilityService eligibility =
            new RebateEligibilityService(
                    mock(OrderRepository.class),
                    payments,
                    repository,
                    slabs,
                    mock(RebateCustomerRepository.class),
                    mock(RebateRedemptionRepository.class));
    final EnhancementProperties features = new EnhancementProperties();
    final CheckoutQuoteService quotes =
            new CheckoutQuoteService(
                    features, validation, calculation, mock(OrderRepository.class), loyalty);
    final MobileCheckoutPreview preview =
            new MobileCheckoutPreview(ownership, quotes, eligibility, loyalty);
    final Rebate offer = new Rebate();

    BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    CalculatedOrderItem line(
            long id, long categoryId, boolean weighted, String subtotal, String tax) {
        var category = new Category();
        category.setId(categoryId);
        var product = new Product();
        product.setId(id);
        product.setName("Test product " + id);
        product.setCategory(category);
        return new CalculatedOrderItem(
                product,
                weighted ? ProductSaleMode.WEIGHT : ProductSaleMode.UNIT,
                weighted ? 1 : 2,
                weighted ? 500 : null,
                n(weighted ? "600" : "50"),
                n("5"),
                n(tax),
                n(subtotal).add(n(tax)));
    }

    CreateOrderRequest request(String code) {
        return new CreateOrderRequest(
                1L,
                2L,
                "Test customer",
                "test-phone",
                PickupType.NORMAL,
                List.of(
                        new CreateOrderItemRequest(10L, 1, 500),
                        new CreateOrderItemRequest(20L, 2, null)),
                null,
                null,
                code);
    }

    @BeforeEach
    void setup() {
        features.setAcceptedCheckoutQuote(true);
        ReflectionTestUtils.setField(
                quotes, "signingKey", "test-key-that-is-long-enough-for-hmac-sha256");
        var data = mock(ValidatedOrderData.class);
        when(validation.validate(any())).thenReturn(data);
        when(calculation.calculate(data))
                .thenReturn(
                        new OrderCalculationResult(
                                List.of(
                                        line(10, 1, true, "300", "15"),
                                        line(20, 2, false, "100", "5")),
                                n("400"),
                                n("20"),
                                n("0"),
                                n("5"),
                                n("0"),
                                n("425")));
        offer.setId(1L);
        offer.setCode("FOOD10");
        offer.setName("Food saving");
        offer.setScope(RebateScope.GENERAL);
        offer.setRebateType(RebateType.PERCENTAGE);
        offer.setRebateValue(n("10"));
        offer.setMaximumDiscountAmount(n("100"));
        offer.setMinimumOrderAmount(n("300"));
        var now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        offer.setValidFrom(now.minusDays(1));
        offer.setValidUntil(now.plusDays(1));
        offer.getExcludedCategoryIds().add(2L);
        when(repository.findActivePublicCandidates(eq(1L), any())).thenReturn(List.of(offer));
        when(repository.findByCodeIgnoreCase("FOOD10")).thenReturn(Optional.of(offer));
    }

    @Test
    void categoryOfferWorksAutomaticallyAndByCodeWithQuotedWeightedAndMultiQuantityLines() {
        var automatic = preview.preview(request(null), "verified");
        assertThat(automatic.quote().items())
                .extracting(CheckoutQuoteService.Line::categoryId)
                .containsExactly(1L, 2L);
        assertThat(automatic.offers()).hasSize(1);
        assertThat(automatic.selectedOffer().rebateAmount()).isEqualByComparingTo("30");
        assertThat(automatic.selectedOffer().payableAfterRebate()).isEqualByComparingTo("395");
        var entered = preview.preview(request("FOOD10"), "verified");
        assertThat(entered.selectedOffer()).isEqualTo(automatic.selectedOffer());
        assertThat(entered.totalBeforeOffer()).isEqualByComparingTo("425");
        verifyNoInteractions(payments);
    }

    @Test
    void exactVariantExclusionWorksWithoutCategoryRules() {
        offer.getExcludedCategoryIds().clear();
        offer.getExcludedProductIds().add(20L);
        assertThat(preview.preview(request("FOOD10"), "verified").selectedOffer().rebateAmount())
                .isEqualByComparingTo("30");
    }

    @Test
    void excludedDrinksCannotUnlockMinimumAndUnionCanExcludeTheWholeBasket() {
        offer.setMinimumOrderAmount(n("301"));
        assertThat(preview.preview(request(null), "verified").offers()).isEmpty();
        assertThatThrownBy(() -> preview.preview(request("FOOD10"), "verified"))
                .isInstanceOf(OfferIneligibleException.class);
        offer.setMinimumOrderAmount(n("0"));
        offer.getExcludedProductIds().add(10L);
        assertThat(preview.preview(request(null), "verified").offers()).isEmpty();
        assertThatThrownBy(() -> preview.preview(request("FOOD10"), "verified"))
                .isInstanceOf(OfferIneligibleException.class);
    }

    void reward() {
        var subject = new VerifiedOrderOwnership.Subject("DEV", UUID.randomUUID());
        when(ownership.verifiedSubject("test-phone", "verified")).thenReturn(subject);
        when(loyalty.enabled()).thenReturn(true);
        when(loyalty.preview(eq("DEV"), eq(subject.id()), any(), isNull()))
                .thenReturn(
                        new LoyaltyService.Reward(
                                "REWARD", "Test reward", 1, n("40"), n("0"), true, null));
    }

    @Test
    void loyaltyAllocationUsesTheSamePricedLines() {
        reward();
        offer.setMinimumOrderAmount(n("270"));
        var result = preview.preview(request("FOOD10"), "verified");
        assertThat(result.selectedOffer().rebateAmount()).isEqualByComparingTo("27");
        assertThat(result.selectedOffer().payableAfterRebate()).isEqualByComparingTo("358");
    }

    @Test
    void slabGuidanceFlowsThroughTheRealMobilePreview() {
        reward();
        offer.setRebateType(RebateType.SLAB);
        offer.setMinimumOrderAmount(n("200"));
        var first = new RebateSlab();
        first.setMinimumOrderAmount(n("200"));
        first.setRebateAmount(n("20"));
        var next = new RebateSlab();
        next.setMinimumOrderAmount(n("350"));
        next.setRebateAmount(n("50"));
        when(slabs.findByRebateIdOrderByMinimumOrderAmountAsc(1L)).thenReturn(List.of(first, next));
        var result = preview.preview(request(null), "verified");
        assertThat(result.selectedOffer().rebateAmount()).isEqualByComparingTo("20");
        assertThat(result.selectedOffer().amountNeededForNextSlab()).isEqualByComparingTo("81.70");
        assertThat(result.spendTargets()).hasSize(1);
        assertThat(result.spendTargets().getFirst().amountNeededForNextSlab())
                .isEqualByComparingTo("81.70");
    }

    @Test
    void unrestrictedOfferRetainsLegacyTaxInclusiveBasis() {
        offer.getExcludedCategoryIds().clear();
        var result = preview.preview(request(null), "verified");
        assertThat(result.selectedOffer().rebateAmount()).isEqualByComparingTo("42");
        assertThat(result.selectedOffer().payableAfterRebate()).isEqualByComparingTo("383");
    }
}
