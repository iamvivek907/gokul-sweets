package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.repository.OrderRepository;
import com.gokulsweets.restaurant.order.service.model.OrderCalculationResult;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckoutQuoteServiceTest {
    private final EnhancementProperties features = new EnhancementProperties();
    private final OrderValidationService validation = mock(OrderValidationService.class);
    private final OrderCalculationService calculation = mock(OrderCalculationService.class);
    private final CheckoutQuoteService service = new CheckoutQuoteService(features, validation,
            calculation, mock(OrderRepository.class));
    private final CreateOrderRequest request = new CreateOrderRequest(1L, 2L, "Customer", "9876543210",
            PickupType.NORMAL, List.of(new CreateOrderItemRequest(3L, 1, null)));
    private final OrderCalculationResult amounts = new OrderCalculationResult(List.of(),
            new BigDecimal("100.00"), new BigDecimal("5.00"),
            new BigDecimal("0.00"), new BigDecimal("105.00"));

    @BeforeEach
    void enable() {
        features.setAcceptedCheckoutQuote(true);
        ReflectionTestUtils.setField(service, "signingKey", "test-key-that-is-long-enough-for-hmac-sha256");
        ValidatedOrderData data = mock(ValidatedOrderData.class);
        when(validation.validate(request)).thenReturn(data);
        when(calculation.calculate(data)).thenReturn(amounts);
    }

    @Test
    void acceptsOnlyTheExactUnexpiredQuote() {
        var quote = service.preview(request, null);
        assertThat(quote.totalAmount()).isEqualTo("105.00");
        service.accept(request, null, amounts, quote.token());
        assertThatThrownBy(() -> service.accept(request, null,
                new OrderCalculationResult(List.of(), amounts.subtotal(), amounts.taxAmount(),
                        amounts.priorityCharge(), new BigDecimal("106.00")), quote.token()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("changed");
        var differentSlot = new CreateOrderRequest(1L, 9L, "Customer", "9876543210",
                PickupType.NORMAL, request.items());
        assertThatThrownBy(() -> service.accept(differentSlot, null, amounts, quote.token()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("changed");
    }

    @Test
    void rejectsMissingExpiredAndTamperedTokens() {
        var quote = service.preview(request, null);
        assertThatThrownBy(() -> service.accept(request, null, amounts, null))
                .isInstanceOf(IllegalStateException.class);
        String expired = "1000000000." + quote.token().split("\\.")[1];
        assertThatThrownBy(() -> service.accept(request, null, amounts, expired))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("expired");
        assertThatThrownBy(() -> service.accept(request, null, amounts,
                quote.token().substring(0, quote.token().length() - 1) + "A"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disabledFeaturePreservesLegacyCheckout() {
        features.setAcceptedCheckoutQuote(false);
        service.accept(request, null, amounts, null);
        assertThatThrownBy(() -> service.preview(request, null)).isInstanceOf(IllegalStateException.class);
    }
}
