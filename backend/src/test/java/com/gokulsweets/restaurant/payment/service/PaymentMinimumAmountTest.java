package com.gokulsweets.restaurant.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.loyalty.LoyaltyService;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.enums.OrderStatus;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.payment.enums.PaymentProviderType;
import com.gokulsweets.restaurant.payment.repository.PaymentRepository;
import com.gokulsweets.restaurant.rebate.RebateApplicationService;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

class PaymentMinimumAmountTest {
    PaymentRepository payments = mock(PaymentRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    RebateApplicationService rebates = mock(RebateApplicationService.class);
    PaymentService service =
            new PaymentService(mock(LoyaltyService.class), payments, orders, rebates);

    Order order(String amount) {
        var order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORDER");
        order.setOrderStatus(OrderStatus.PENDING_PAYMENT);
        order.setReservationExpiresAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).plusMinutes(10));
        order.setTotalAmount(new BigDecimal(amount));
        when(orders.findForUpdate("ORDER")).thenReturn(Optional.of(order));
        ReflectionTestUtils.setField(service, "paymentExpiryMinutes", 5L);
        return order;
    }

    @Test
    void rejectsDiscountedSubRupeeRazorpayAmountBeforeSavingAttempt() {
        var order = order("10");
        doAnswer(
                        invocation -> {
                            order.setTotalAmount(new BigDecimal("0.99"));
                            return null;
                        })
                .when(rebates)
                .revalidateAppliedRebateBeforePayment(order);
        assertThatThrownBy(
                        () -> service.createPendingPayment("ORDER", PaymentProviderType.RAZORPAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100 paise");
        verify(payments, never()).save(any());
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void acceptsExactlyOneRupeeAndDoesNotApplyRazorpayMinimumToPhonepe() {
        order("1.00");
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.createPendingPayment("ORDER", PaymentProviderType.RAZORPAY).getAmount())
                .isEqualByComparingTo("1.00");
        order("0.99");
        assertThat(service.createPendingPayment("ORDER", PaymentProviderType.PHONEPE).getAmount())
                .isEqualByComparingTo("0.99");
    }
}
