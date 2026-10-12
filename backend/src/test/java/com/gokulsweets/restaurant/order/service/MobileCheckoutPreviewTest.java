package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.customer.identity.VerifiedOrderOwnership;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.rebate.RebateEligibilityService;

import org.junit.jupiter.api.Test;

import java.util.List;

class MobileCheckoutPreviewTest {
    final VerifiedOrderOwnership ownership = mock(VerifiedOrderOwnership.class);
    final CheckoutQuoteService quotes = mock(CheckoutQuoteService.class);
    final RebateEligibilityService rebates = mock(RebateEligibilityService.class);
    final com.gokulsweets.restaurant.loyalty.LoyaltyService loyalty =
            mock(com.gokulsweets.restaurant.loyalty.LoyaltyService.class);
    final MobileCheckoutPreview preview =
            new MobileCheckoutPreview(ownership, quotes, rebates, loyalty);
    final CreateOrderRequest request =
            new CreateOrderRequest(1L, 2L, "Customer", "9876543210", PickupType.NORMAL, List.of());

    @Test
    void identityMustBeCheckedBeforePricing() {
        doThrow(new IllegalArgumentException("Verify phone"))
                .when(ownership)
                .requireVerifiedIdentity("9876543210", "expired");
        assertThatThrownBy(() -> preview.preview(request, "expired")).hasMessage("Verify phone");
        verifyNoInteractions(quotes, rebates);
    }

    @Test
    void serverQuoteBecomesAnUnsavedOfferDraftWithFeeAndIdentity() {
        var quote =
                new CheckoutQuoteService.Quote(
                        List.of(),
                        "100",
                        "5",
                        "0",
                        "10",
                        "0",
                        "117.30",
                        "INR",
                        "2099-01-01T00:00:00Z",
                        "signed",
                        "2.30",
                        "0",
                        "2",
                        "0");
        when(quotes.preview(request, null)).thenReturn(quote);
        when(rebates.previewDraft(any()))
                .thenAnswer(
                        call -> {
                            var draft =
                                    call.getArgument(
                                            0, com.gokulsweets.restaurant.order.entity.Order.class);
                            assertThat(draft.getId()).isNull();
                            assertThat(draft.getBranch().getId()).isEqualTo(1);
                            assertThat(draft.getCustomerPhone()).isEqualTo("9876543210");
                            assertThat(draft.getPaymentFeeRate()).isEqualByComparingTo("2");
                            assertThat(draft.getConvenienceFee()).isEqualByComparingTo("10");
                            return List.of();
                        });
        var result = preview.preview(request, "verified");
        assertThat(result.quote()).isEqualTo(quote);
        assertThat(result.paymentFee()).isEqualByComparingTo("2.30");
        assertThat(result.paymentFeeTax()).isZero();
        var sequence = inOrder(ownership, quotes, rebates);
        sequence.verify(ownership).requireVerifiedIdentity("9876543210", "verified");
        sequence.verify(quotes).preview(request, null);
        sequence.verify(rebates).previewDraft(any());
    }

    @Test
    void exposesRoundedFeeAndIncludedTaxAfterTheBestRebate() {
        var quote =
                new CheckoutQuoteService.Quote(
                        List.of(),
                        "100",
                        "5",
                        "0",
                        "10",
                        "0",
                        "117.30",
                        "INR",
                        "2099-01-01T00:00:00Z",
                        "signed",
                        "2.30",
                        "0.35",
                        "2",
                        "18");
        when(quotes.preview(request, null)).thenReturn(quote);
        var best =
                new com.gokulsweets.restaurant.rebate.dto.AvailableRebateResponse(
                        1L,
                        "SAVE20",
                        "Save 20",
                        null,
                        com.gokulsweets.restaurant.rebate.RebateScope.GENERAL,
                        com.gokulsweets.restaurant.rebate.RebateType.FIXED_AMOUNT,
                        new java.math.BigDecimal("20"),
                        new java.math.BigDecimal("96.90"),
                        null,
                        null,
                        null,
                        null,
                        null);
        when(rebates.previewDraft(any())).thenReturn(List.of(best));
        var result = preview.preview(request, "verified");
        assertThat(result.paymentFee()).isEqualByComparingTo("1.90");
        assertThat(result.paymentFeeTax()).isEqualByComparingTo("0.29");
        assertThat(new java.math.BigDecimal("95").add(result.paymentFee()))
                .isEqualByComparingTo(result.offers().getFirst().payableAfterRebate());
    }

    @Test
    void normalizedQuoteIdsHandleDuplicateRowsAndExcludedProductsWithoutIndexingTheRequest() {
        var duplicate =
                new CreateOrderRequest(
                        1L,
                        2L,
                        "Customer",
                        "9876543210",
                        PickupType.NORMAL,
                        List.of(
                                new com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest(
                                        1L, 1, null),
                                new com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest(
                                        1L, 1, null),
                                new com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest(
                                        2L, 1, null)));
        var quote =
                new CheckoutQuoteService.Quote(
                        List.of(
                                new CheckoutQuoteService.Line(
                                        "Excluded", "100", "5", "5", "105", 2L),
                                new CheckoutQuoteService.Line(
                                        "Eligible merged row", "100", "5", "10", "210", 1L)),
                        "300",
                        "15",
                        "0",
                        "0",
                        "0",
                        "315",
                        "INR",
                        "2099-01-01T00:00:00Z",
                        "signed",
                        "0",
                        "0",
                        "0",
                        "0");
        var subject = new VerifiedOrderOwnership.Subject("DEV", java.util.UUID.randomUUID());
        when(quotes.preview(duplicate, null)).thenReturn(quote);
        when(loyalty.enabled()).thenReturn(true);
        when(ownership.verifiedSubject("9876543210", "verified")).thenReturn(subject);
        when(loyalty.excludedProducts()).thenReturn(java.util.Set.of(2L));
        when(rebates.previewDraft(any())).thenReturn(List.of());
        assertThat(preview.preview(duplicate, "verified").quote().items()).hasSize(2);
        verify(loyalty).wallet("DEV", subject.id(), new java.math.BigDecimal("200"));
        verify(loyalty).preview("DEV", subject.id(), new java.math.BigDecimal("200"), null);
    }

    @Test
    void explicitMobileOfferUsesRealDraftEligibilityWithoutAnOrderStatus() {
        var repository = mock(com.gokulsweets.restaurant.rebate.RebateRepository.class);
        var payments = mock(com.gokulsweets.restaurant.payment.repository.PaymentRepository.class);
        var realEligibility =
                new RebateEligibilityService(
                        mock(com.gokulsweets.restaurant.order.repository.OrderRepository.class),
                        payments,
                        repository,
                        mock(com.gokulsweets.restaurant.rebate.RebateSlabRepository.class),
                        mock(com.gokulsweets.restaurant.rebate.RebateCustomerRepository.class),
                        mock(com.gokulsweets.restaurant.rebate.RebateRedemptionRepository.class));
        var offer = new com.gokulsweets.restaurant.rebate.Rebate();
        offer.setId(4L);
        offer.setCode("SAVE8");
        offer.setName("Chosen weaker offer");
        offer.setScope(com.gokulsweets.restaurant.rebate.RebateScope.GENERAL);
        offer.setRebateType(com.gokulsweets.restaurant.rebate.RebateType.FIXED_AMOUNT);
        offer.setRebateValue(new java.math.BigDecimal("8"));
        var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata"));
        offer.setValidFrom(now.minusDays(1));
        offer.setValidUntil(now.plusDays(1));
        when(repository.findByCodeIgnoreCase("SAVE8")).thenReturn(java.util.Optional.of(offer));
        when(repository.findActivePublicCandidates(eq(1L), any())).thenReturn(List.of(offer));
        var selected =
                new CreateOrderRequest(
                        1L,
                        2L,
                        "Customer",
                        "9876543210",
                        PickupType.NORMAL,
                        List.of(),
                        null,
                        null,
                        "SAVE8");
        when(quotes.preview(selected, null))
                .thenReturn(
                        new CheckoutQuoteService.Quote(
                                List.of(),
                                "100",
                                "0",
                                "0",
                                "0",
                                "0",
                                "102",
                                "INR",
                                "2099-01-01T00:00:00Z",
                                "signed",
                                "2",
                                "0",
                                "2",
                                "0"));
        var result =
                new MobileCheckoutPreview(ownership, quotes, realEligibility, loyalty)
                        .preview(selected, "verified");
        assertThat(result.selectedOffer().code()).isEqualTo("SAVE8");
        assertThat(result.selectedOffer().payableAfterRebate()).isEqualByComparingTo("93.84");
        when(repository.findActivePublicCandidates(eq(1L), any())).thenReturn(List.of());
        var entered =
                new MobileCheckoutPreview(ownership, quotes, realEligibility, loyalty)
                        .preview(selected, "verified");
        assertThat(entered.selectedOffer().code()).isEqualTo("SAVE8");
        assertThat(entered.offers()).hasSize(1);
        verifyNoInteractions(payments);
    }

    @Test
    void explicitOfferRejectionHasADistinctRecoveryCode() {
        var selected =
                new CreateOrderRequest(
                        1L,
                        2L,
                        "Customer",
                        "9876543210",
                        PickupType.NORMAL,
                        List.of(),
                        null,
                        null,
                        "OLD");
        when(quotes.preview(selected, null))
                .thenReturn(
                        new CheckoutQuoteService.Quote(
                                List.of(),
                                "100",
                                "0",
                                "0",
                                "0",
                                "0",
                                "100",
                                "INR",
                                "2099-01-01T00:00:00Z",
                                "signed",
                                "0",
                                "0",
                                "0",
                                "0"));
        when(rebates.previewDraft(any())).thenReturn(List.of());
        when(rebates.findEligibleDraftRebate(any(), eq("OLD")))
                .thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> preview.preview(selected, "verified"))
                .isInstanceOf(OfferIneligibleException.class);
        var response =
                new com.gokulsweets.restaurant.exception.GlobalExceptionHandler()
                        .handleIneligibleOffer(
                                new OfferIneligibleException(),
                                new org.springframework.mock.web.MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("OFFER_INELIGIBLE");
    }
}
