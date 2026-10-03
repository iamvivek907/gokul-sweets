package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.FulfillmentType;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.PaymentFeePricing;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.rebate.dto.ApplyRebateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryRebatePricingTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final RebateRepository rebates = mock(RebateRepository.class);
    final RebateEligibilityService eligibility = new RebateEligibilityService(orders, payments, rebates,
            mock(RebateSlabRepository.class), mock(RebateCustomerRepository.class), mock(RebateRedemptionRepository.class));
    final RebateApplicationService application = new RebateApplicationService(orders, rebates, payments, eligibility);

    BigDecimal n(String value) { return new BigDecimal(value); }

    Order order(String rate, String taxRate) {
        var order = new Order();
        order.setId(1L); order.setOrderNumber("DELIVERY-TEST");
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT); order.setFulfillmentType(FulfillmentType.DELIVERY);
        var branch = new Branch(); branch.setId(2L); order.setBranch(branch);
        order.setSubtotal(n("100")); order.setTaxAmount(n("5")); order.setPriorityCharge(n("0"));
        order.setConvenienceFee(n("10")); order.setDeliveryFee(n("50"));
        order.setPaymentFeeRate(n(rate)); order.setPaymentFeeTaxRate(n(taxRate));
        PaymentFeePricing.reprice(order);
        when(orders.findForUpdate("DELIVERY-TEST")).thenReturn(Optional.of(order));
        when(orders.findDetailedByOrderNumber("DELIVERY-TEST")).thenReturn(Optional.of(order));
        return order;
    }

    Rebate rebate() {
        var rebate = new Rebate(); rebate.setId(3L); rebate.setCode("SAVE20"); rebate.setName("Save 20");
        rebate.setScope(RebateScope.GENERAL); rebate.setRebateType(RebateType.FIXED_AMOUNT); rebate.setRebateValue(n("20"));
        var now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        rebate.setValidFrom(now.minusDays(1)); rebate.setValidUntil(now.plusDays(1));
        when(rebates.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(rebate));
        when(rebates.findById(3L)).thenReturn(Optional.of(rebate));
        when(rebates.findActivePublicCandidates(eq(2L), any())).thenReturn(List.of(rebate));
        return rebate;
    }

    @Test void draftPreviewDoesNotCreateAnOrderAndUsesTheSameFeeRules() {
        var order=order("2", "0"); order.setId(null); order.setOrderNumber(null); rebate();
        clearInvocations(orders, payments);
        assertThat(eligibility.previewDraft(order).getFirst().payableAfterRebate()).isEqualByComparingTo("147.90");
        verifyNoInteractions(orders, payments);
    }

    @Test void explicitDraftOffersUseRealEligibilityWithoutPersistedPaymentGuards() {
        var draft=order("2","0"); draft.setId(null); draft.setOrderNumber(null); draft.setOrderStatus(null);
        var offer=rebate(); clearInvocations(orders,payments);
        assertThat(eligibility.findEligibleDraftRebate(draft," save20 ").orElseThrow().payableAfterRebate()).isEqualByComparingTo("147.90");
        offer.setMinimumOrderAmount(n("106"));
        assertThat(eligibility.findEligibleDraftRebate(draft,"SAVE20")).isEmpty();
        offer.setMinimumOrderAmount(n("100")); offer.setActive(false);
        assertThat(eligibility.findEligibleDraftRebate(draft,"SAVE20")).isEmpty();
        verifyNoInteractions(orders,payments);
    }

    @Test void draftEntryCannotBypassPersistedOrderOrStartedPaymentGuards() {
        var saved=order("2","0"); rebate();
        assertThatThrownBy(()->eligibility.findEligibleDraftRebate(saved,"SAVE20")).hasMessageContaining("unsaved");
        saved.setOrderStatus(OrderStatus.CONFIRMED);
        assertThat(eligibility.findEligibleRebate(saved,"SAVE20")).isEmpty();
        saved.setOrderStatus(OrderStatus.PENDING_PAYMENT); when(payments.existsByOrderId(1L)).thenReturn(true);
        assertThat(eligibility.findEligibleRebate(saved,"SAVE20")).isEmpty();
    }

    @Test void deliveryPreviewApplyAndRemovePreserveChargeWithInclusivePercentageFee() {
        var order = order("2", "18"); rebate();
        var preview = eligibility.getAvailableRebates("DELIVERY-TEST").getFirst();
        assertThat(preview.payableAfterRebate()).isEqualByComparingTo("147.90");
        var applied = application.apply("DELIVERY-TEST", new ApplyRebateRequest("SAVE20"));
        assertThat(applied.totalAmount()).isEqualByComparingTo(preview.payableAfterRebate());
        assertThat(applied.paymentFee()).isEqualByComparingTo("2.90");
        assertThat(applied.paymentFeeTax()).isEqualByComparingTo("0.44");
        assertThat(applied.amountBeforeRebate()).isEqualByComparingTo("165");
        assertThat(application.apply("DELIVERY-TEST", new ApplyRebateRequest("SAVE20")).totalAmount()).isEqualByComparingTo("147.90");
        var removed = application.remove("DELIVERY-TEST");
        assertThat(removed.totalAmount()).isEqualByComparingTo("168.30");
        assertThat(removed.paymentFee()).isEqualByComparingTo("3.30");
        assertThat(removed.paymentFeeTax()).isEqualByComparingTo("0.50");
        assertThat(application.remove("DELIVERY-TEST").totalAmount()).isEqualByComparingTo("168.30");
        assertThat(order.getDeliveryFee()).isEqualByComparingTo("50");
    }

    @Test void disabledPaymentFeeStillRetainsDeliveryAndDoesNotEarnRebateThresholds() {
        var order = order("0", "0"); var rebate = rebate();
        assertThat(eligibility.getAvailableRebates("DELIVERY-TEST").getFirst().payableAfterRebate()).isEqualByComparingTo("145");
        assertThat(application.apply("DELIVERY-TEST", new ApplyRebateRequest("SAVE20")).totalAmount()).isEqualByComparingTo("145");
        assertThat(application.remove("DELIVERY-TEST").totalAmount()).isEqualByComparingTo("165");
        rebate.setMinimumOrderAmount(n("120"));
        assertThat(eligibility.getAvailableRebates("DELIVERY-TEST")).isEmpty();
        assertThat(order.getPaymentFee()).isEqualByComparingTo("0");
    }

    @Test void invalidOfferRemovalPreservesDeliveryAndTaxOffSnapshot() {
        var order = order("2", "0"); var rebate = rebate();
        var applied = application.apply("DELIVERY-TEST", new ApplyRebateRequest("SAVE20"));
        assertThat(applied.totalAmount()).isEqualByComparingTo("147.90");
        assertThat(applied.paymentFeeTax()).isEqualByComparingTo("0");
        rebate.setActive(false);
        assertThatThrownBy(() -> application.revalidateAppliedRebateBeforePayment(order))
                .hasMessageContaining("Please review the updated order total");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("168.30");
        assertThat(order.getDeliveryFee()).isEqualByComparingTo("50");
        assertThat(order.getPaymentFeeTax()).isEqualByComparingTo("0");
    }

    @Test void rewardsApplyBeforeCouponMinimumAndNeverDiscountFees() {
        var order=order("2","0");order.setSubtotal(n("150"));order.setLoyaltyEnrolled(true);order.setLoyaltyDiscount(n("5"));PaymentFeePricing.reprice(order);
        var rebate=rebate();rebate.setMinimumOrderAmount(n("150"));
        assertThat(eligibility.getAvailableRebates("DELIVERY-TEST")).isEmpty();
        assertThatThrownBy(()->application.apply("DELIVERY-TEST",new ApplyRebateRequest("SAVE20"))).hasMessageContaining("minimum");
        rebate.setMinimumOrderAmount(n("145"));
        assertThat(eligibility.getAvailableRebates("DELIVERY-TEST").getFirst().payableAfterRebate()).isEqualByComparingTo("193.80");
        assertThat(application.apply("DELIVERY-TEST",new ApplyRebateRequest("SAVE20")).totalAmount()).isEqualByComparingTo("193.80");
        assertThat(application.remove("DELIVERY-TEST").totalAmount()).isEqualByComparingTo("214.20");
        assertThat(order.getLoyaltyDiscount()).isEqualByComparingTo("5");
    }
    @Test void evenLargeCouponCannotConsumeTaxConvenienceOrDeliveryFees() {
        var order=order("2","0");order.setSubtotal(n("150"));order.setLoyaltyEnrolled(true);order.setLoyaltyDiscount(n("5"));PaymentFeePricing.reprice(order);
        rebate().setRebateValue(n("1000"));
        var applied=application.apply("DELIVERY-TEST",new ApplyRebateRequest("SAVE20"));
        assertThat(applied.rebateAmount()).isEqualByComparingTo("145");
        assertThat(applied.totalAmount()).isEqualByComparingTo("66.30");
    }
}
