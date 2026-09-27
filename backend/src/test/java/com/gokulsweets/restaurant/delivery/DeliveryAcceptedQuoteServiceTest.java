package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.model.CalculatedOrderItem;
import com.gokulsweets.restaurant.order.service.model.OrderCalculationResult;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeliveryAcceptedQuoteServiceTest {
    private final EnhancementProperties flags = new EnhancementProperties();
    private final DeliveryOrderPreparationService preparation = mock(DeliveryOrderPreparationService.class);
    private final Clock now = Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC);
    private final DeliveryAcceptedQuoteService service = new DeliveryAcceptedQuoteService(flags, preparation, now);

    @Test
    void signsExactCartAddressWindowAndPriceAndExpires() {
        flags.setDeliveryAcceptedQuote(true);
        flags.setDeliveryRiderHolds(true);
        flags.setDeliveryAddressBoundaries(true);
        ReflectionTestUtils.setField(service, "signingKey", "delivery-quote-test-signing-key-at-least-32-characters");
        var product = new Product();
        product.setId(42L);
        var amount = new BigDecimal("100.00");
        var price = new OrderCalculationResult(List.of(new CalculatedOrderItem(product,
                ProductSaleMode.UNIT, 1, null, amount, BigDecimal.ZERO, BigDecimal.ZERO, amount)),
                amount, BigDecimal.ZERO, BigDecimal.ZERO, amount);
        var quote = new DeliveryCapacityService.QuoteRequest(1L, "Hazratganj", "226001",
                LocalDate.of(2026, 9, 28), List.of(new CreateOrderItemRequest(42L, 1, null)), 26.85, 80.94);
        var draft = new DeliveryOrderCreationService.CreateRequest(quote, 2L,
                "Customer", "9999999999", "12 Main Road", null);
        when(preparation.prepare(quote, 2L)).thenReturn(new DeliveryOrderPreparationService.Prepared(null, price, null));
        String token = service.preview(draft).token();
        var accepted = new DeliveryOrderCreationService.CreateRequest(quote, 2L,
                "Customer", "9999999999", "12 Main Road", token);
        service.accept(accepted, price);

        assertThatThrownBy(() -> service.accept(new DeliveryOrderCreationService.CreateRequest(
                quote, 3L, "Customer", "9999999999", "12 Main Road", token), price))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.accept(new DeliveryOrderCreationService.CreateRequest(
                quote, 2L, "Customer", "9999999999", "13 Main Road", token), price))
                .isInstanceOf(IllegalStateException.class);
        var changedCart = new DeliveryCapacityService.QuoteRequest(1L, "Hazratganj", "226001",
                quote.serviceDate(), List.of(new CreateOrderItemRequest(42L, 2, null)), 26.85, 80.94);
        assertThatThrownBy(() -> service.accept(new DeliveryOrderCreationService.CreateRequest(
                changedCart, 2L, "Customer", "9999999999", "12 Main Road", token), price))
                .isInstanceOf(IllegalStateException.class);
        var increased = new OrderCalculationResult(price.items(), amount, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("101.00"));
        assertThatThrownBy(() -> service.accept(accepted, increased)).isInstanceOf(IllegalStateException.class);
        var later = new DeliveryAcceptedQuoteService(flags, preparation,
                Clock.fixed(Instant.now(now).plusSeconds(301), ZoneOffset.UTC));
        ReflectionTestUtils.setField(later, "signingKey", "delivery-quote-test-signing-key-at-least-32-characters");
        assertThatThrownBy(() -> later.accept(accepted, price)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disabledOrMissingSecretFailsClosed() {
        var quote = new DeliveryCapacityService.QuoteRequest(1L, "Hazratganj", "226001",
                LocalDate.of(2026, 9, 28), List.of(new CreateOrderItemRequest(42L, 1, null)), 26.85, 80.94);
        var draft = new DeliveryOrderCreationService.CreateRequest(quote, 2L,
                "Customer", "9999999999", "12 Main Road", null);
        assertThatThrownBy(() -> service.preview(draft)).isInstanceOf(IllegalStateException.class);
        flags.setDeliveryAcceptedQuote(true);
        flags.setDeliveryRiderHolds(true);
        flags.setDeliveryAddressBoundaries(true);
        var amount = new BigDecimal("100.00");
        when(preparation.prepare(quote, 2L)).thenReturn(new DeliveryOrderPreparationService.Prepared(null,
                new OrderCalculationResult(List.of(), amount, BigDecimal.ZERO, BigDecimal.ZERO, amount), null));
        assertThatThrownBy(() -> service.preview(draft)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signing key");
    }
}
