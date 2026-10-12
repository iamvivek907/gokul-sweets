package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.dto.CreateOrderRequest;
import com.gokulsweets.restaurant.order.entity.Order;
import com.gokulsweets.restaurant.order.entity.OrderIdempotency;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.repository.OrderIdempotencyRepository;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

class OrderIdempotencyServiceTest {
    private final OrderIdempotencyRepository repository = mock(OrderIdempotencyRepository.class);
    private final OrderIdempotencyService service = new OrderIdempotencyService(repository);

    private CreateOrderRequest request(String token, String reward, String offer) {
        return new CreateOrderRequest(
                1L,
                2L,
                "Customer",
                "9876543210",
                PickupType.NORMAL,
                List.of(new CreateOrderItemRequest(3L, 2, null)),
                token,
                reward,
                offer);
    }

    @Test
    void selectionChangesRejectReusedKeysWhileQuoteRefreshReturnsTheExistingOrder() {
        var original = request("quote-one", "SWEET_5", "SAVE10");
        var existing = new OrderIdempotency();
        var order = new Order();
        order.setId(7L);
        existing.setOrder(order);
        existing.setRequestHash(service.createRequestHash(original));
        when(repository.claim(anyString(), anyString())).thenReturn(0);
        when(repository.findByIdempotencyKey("retry-key")).thenReturn(Optional.of(existing));
        assertThat(
                        service.claim(
                                        "retry-key",
                                        service.createRequestHash(
                                                request("quote-two", "SWEET_5", "SAVE10")))
                                .existingOrder())
                .isSameAs(order);
        for (var changed :
                List.of(
                        request("quote-two", "SWEET_10", "SAVE10"),
                        request("quote-two", null, "SAVE10"),
                        request("quote-two", "SWEET_5", "SAVE20"))) {
            assertThatThrownBy(() -> service.claim("retry-key", service.createRequestHash(changed)))
                    .hasMessageContaining("different order request");
        }
    }

    @Test
    void blankSelectionsKeepTheHistoricalHashAndCouponNormalizationIsStable() throws Exception {
        var historical =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                "1|2|Customer|9876543210|NORMAL|3:2;"
                                                        .getBytes(StandardCharsets.UTF_8)));
        assertThat(service.createRequestHash(request("fresh-token", null, null)))
                .isEqualTo(historical);
        assertThat(service.createRequestHash(request("fresh-token", "  ", "  ")))
                .isEqualTo(historical);
        assertThat(service.createRequestHash(request("one", "SWEET_5", " save10 ")))
                .isEqualTo(service.createRequestHash(request("two", "SWEET_5", "SAVE10")));
    }

    @Test
    void weightedSelectionsAlsoChangeTheHash() {
        var weighted =
                new CreateOrderRequest(
                        1L,
                        2L,
                        "Customer",
                        "9876543210",
                        PickupType.NORMAL,
                        List.of(new CreateOrderItemRequest(3L, null, 500)),
                        "quote",
                        "SWEET_5",
                        null);
        var unselected =
                new CreateOrderRequest(
                        1L,
                        2L,
                        "Customer",
                        "9876543210",
                        PickupType.NORMAL,
                        weighted.items(),
                        "new-quote",
                        null,
                        null);
        assertThat(service.createRequestHash(weighted))
                .isNotEqualTo(service.createRequestHash(unselected));
    }
}
