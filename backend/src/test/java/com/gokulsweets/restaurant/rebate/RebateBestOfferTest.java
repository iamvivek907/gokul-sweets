package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.rebate.dto.AvailableRebateResponse;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RebateBestOfferTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final RebateRepository rebates = mock(RebateRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final RebateEligibilityService eligibility = mock(RebateEligibilityService.class);
    final RebateApplicationService service = new RebateApplicationService(orders, rebates, payments, eligibility);
    Order order(String total) {
        var order = new Order(); order.setId(1L); order.setOrderNumber("TEST"); order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setSubtotal(new BigDecimal("100")); order.setTaxAmount(new BigDecimal("5")); order.setPriorityCharge(BigDecimal.ZERO);
        order.setConvenienceFee(new BigDecimal("10")); order.setTotalAmount(new BigDecimal(total));
        when(orders.findForUpdate("TEST")).thenReturn(Optional.of(order)); return order;
    }
    AvailableRebateResponse offer(String discount,String payable) {
        return new AvailableRebateResponse(1L,"BEST","Best offer",null,RebateScope.GENERAL,RebateType.FIXED_AMOUNT,
                new BigDecimal(discount),new BigDecimal(payable),null,null,null,null,null);
    }
    @Test void preservesAStrongerSelectedPrivateOffer() {
        var order = order("75"); order.setRebateCode("PRIVATE"); order.setRebateDiscountAmount(new BigDecimal("40"));
        when(eligibility.getAvailableRebates("TEST")).thenReturn(List.of(offer("20","95")));
        var result = service.applyBest("TEST");
        assertThat(result.rebateCode()).isEqualTo("PRIVATE"); assertThat(result.totalAmount()).isEqualByComparingTo("75");
        verify(orders, never()).save(any()); verify(eligibility, never()).getEligibleRebate(any(),any());
    }
    @Test void revalidatesAnImprovingOfferUnderTheOrderLock() {
        var order = order("115"); var best = offer("20","95");
        when(eligibility.getAvailableRebates("TEST")).thenReturn(List.of(best));
        when(eligibility.getEligibleRebate(order,"BEST")).thenReturn(best);
        var rebate = new Rebate(); rebate.setId(1L); rebate.setCode("BEST"); rebate.setName("Best offer");
        when(rebates.findById(1L)).thenReturn(Optional.of(rebate));
        assertThat(service.applyBest("TEST").totalAmount()).isEqualByComparingTo("95");
        verify(eligibility).getEligibleRebate(order,"BEST");
    }
    @Test void aReconfiguredOfferCannotRaiseAnAlreadyDiscountedTotal() {
        var order = order("75");
        when(eligibility.getAvailableRebates("TEST")).thenReturn(List.of(offer("50","65")));
        when(eligibility.getEligibleRebate(order,"BEST")).thenReturn(offer("10","105"));
        var rebate = new Rebate(); rebate.setId(1L); rebate.setCode("BEST");
        when(rebates.findById(1L)).thenReturn(Optional.of(rebate));
        assertThatThrownBy(() -> service.applyBest("TEST")).isInstanceOf(IllegalStateException.class).hasMessageContaining("offer changed");
    }
    @Test void automaticOffersCannotChangeAStartedPayment() {
        order("115"); when(payments.existsByOrderId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.applyBest("TEST")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(eligibility,rebates);
    }
    @Test void choosesLowestPayableFromMultipleEligibleOffers() {
        var order=order("115"); var best=offer("30","85");
        var smaller=new AvailableRebateResponse(2L,"SMALL","Small offer",null,RebateScope.GENERAL,RebateType.FIXED_AMOUNT,
                new BigDecimal("10"),new BigDecimal("105"),null,null,null,null,null);
        when(eligibility.getAvailableRebates("TEST")).thenReturn(List.of(smaller,best));
        when(eligibility.getEligibleRebate(order,"BEST")).thenReturn(best);
        var rebate=new Rebate();rebate.setId(1L);rebate.setCode("BEST");rebate.setName("Best offer");
        when(rebates.findById(1L)).thenReturn(Optional.of(rebate));
        var result=service.applyBest("TEST");
        assertThat(result.rebateCode()).isEqualTo("BEST");assertThat(result.totalAmount()).isEqualByComparingTo("85");
        verify(eligibility,never()).getEligibleRebate(order,"SMALL");
    }
}
