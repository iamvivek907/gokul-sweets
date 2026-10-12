package com.gokulsweets.restaurant.loyalty;

import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.rebate.RebateApplicationService;

import org.junit.jupiter.api.Test;

import java.util.Optional;

class LoyaltyCheckoutServiceTest {
    @Test
    void rewardSelectionPassesTheManualCouponForRevalidation() {
        var orders = mock(OrderRepository.class);
        var payments = mock(PaymentRepository.class);
        var loyalty = mock(LoyaltyService.class);
        var rebates = mock(RebateApplicationService.class);
        var order = new Order();
        order.setId(1L);
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setRebateManualSelection(true);
        order.setRebateCode("CHOSEN");
        when(orders.findForUpdate("ORDER")).thenReturn(Optional.of(order));
        when(loyalty.enabled()).thenReturn(true);
        doAnswer(
                        call -> {
                            order.setRebateManualSelection(false);
                            order.setRebateCode(null);
                            return null;
                        })
                .when(rebates)
                .remove("ORDER");
        new LoyaltyCheckoutService(orders, payments, loyalty, rebates)
                .select("ORDER", "SWEET_5", "version");
        verify(rebates).reapplyManualOrBest("ORDER", "CHOSEN");
        verify(rebates, never()).applyBest(any());
    }
}
