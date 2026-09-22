package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.menu.dto.MenuCategoryResponse;
import com.gokulsweets.restaurant.menu.dto.MenuProductResponse;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.service.CartAvailabilityService;
import com.gokulsweets.restaurant.product.ProductSaleMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorefrontHighlightsControllerTest {
    private final EnhancementProperties features = new EnhancementProperties();
    private final MenuService menu = mock(MenuService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CartAvailabilityService availability = mock(CartAvailabilityService.class);
    private final Clock clock = mock(Clock.class);
    private final Instant now = Instant.parse("2026-09-22T04:30:00Z");
    private final StorefrontHighlightsController controller =
            new StorefrontHighlightsController(features, menu, jdbc, availability, clock);

    @BeforeEach void setUp() {
        features.setCustomerHomeV2(true);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Kolkata"));
        when(clock.instant()).thenReturn(now);
        var product = new MenuProductResponse(11L, 1L, "Sweets", "Sweet", null,
                BigDecimal.TEN, null, true, ProductSaleMode.WEIGHT, 250, 50);
        when(menu.getMenu(anyLong())).thenReturn(List.of(new MenuCategoryResponse(1L, "Sweets", null, 0, List.of(product))));
        when(jdbc.queryForList(anyString(), eq(Long.class), anyLong(), any(Date.class), any(Date.class)))
                .thenReturn(List.of(11L));
        when(jdbc.queryForList(anyString(), eq(Long.class), anyLong(), any(Date.class)))
                .thenReturn(List.of(11L));
        when(availability.check(anyLong(), any(LocalDate.class), anyInt(), anyList())).thenReturn(result(true));
    }

    @Test void reusesHighlightsWithoutRepeatingQueriesOrAvailabilityChecks() {
        var first = controller.highlights(1L);
        when(clock.instant()).thenReturn(now.plusSeconds(29));
        assertThat(controller.highlights(1L)).isSameAs(first);
        assertThat(first.trendingProductIds()).containsExactly(11L);
        assertThat(first.newProductIds()).containsExactly(11L);
        verify(menu).getMenu(1L);
        verify(availability).check(1L, LocalDate.now(clock), 31, List.of(new CreateOrderItemRequest(11L, null, 250)));
        verify(jdbc).queryForList(anyString(), eq(Long.class), eq(1L), any(Date.class), any(Date.class));
        verify(jdbc).queryForList(anyString(), eq(Long.class), eq(1L), any(Date.class));
        verifyNoMoreInteractions(menu, jdbc, availability);
    }

    @Test void refreshesAtExpiryAndCachesEmptyResults() {
        controller.highlights(1L);
        when(clock.instant()).thenReturn(now.plusSeconds(30));
        when(availability.check(anyLong(), any(LocalDate.class), anyInt(), anyList())).thenReturn(result(false));
        var refreshed = controller.highlights(1L);
        assertThat(refreshed.trendingProductIds()).isEmpty();
        assertThat(refreshed.newProductIds()).isEmpty();
        assertThat(controller.highlights(1L)).isSameAs(refreshed);
        verify(menu, times(2)).getMenu(1L);
        verify(availability, times(2)).check(eq(1L), any(LocalDate.class), eq(31), anyList());
    }

    @Test void isolatesBranchesAndBypassesCachedResultsWhenDisabled() {
        controller.highlights(1L);
        controller.highlights(2L);
        verify(menu).getMenu(1L);
        verify(menu).getMenu(2L);
        clearInvocations(menu, jdbc, availability);
        features.setCustomerHomeV2(false);
        assertThat(controller.highlights(1L).trendingProductIds()).isEmpty();
        assertThat(controller.highlights(3L).newProductIds()).isEmpty();
        verifyNoInteractions(menu, jdbc, availability);
    }

    @Test void refreshesWhenBusinessDateOrOrderingWindowChanges() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-22T18:29:59Z"));
        controller.highlights(1L);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-22T18:30:00Z"));
        controller.highlights(1L);
        features.setFutureOrderingDays(10);
        controller.highlights(1L);
        verify(availability).check(eq(1L), eq(LocalDate.of(2026, 9, 22)), eq(31), anyList());
        verify(availability).check(eq(1L), eq(LocalDate.of(2026, 9, 23)), eq(31), anyList());
        verify(availability).check(eq(1L), eq(LocalDate.of(2026, 9, 23)), eq(11), anyList());
    }

    @Test void evictsLeastRecentlyUsedEntriesToBoundMemory() {
        for (long branch = 1; branch <= 128; branch++) controller.highlights(branch);
        controller.highlights(1L);
        controller.highlights(129L);
        controller.highlights(1L);
        controller.highlights(2L);
        verify(menu).getMenu(1L);
        verify(menu, times(2)).getMenu(2L);
    }

    @Test void retriesFailedLoadsRatherThanCachingFailures() {
        when(menu.getMenu(1L)).thenThrow(new IllegalStateException("Temporary failure")).thenReturn(List.of());
        assertThatThrownBy(() -> controller.highlights(1L)).isInstanceOf(IllegalStateException.class);
        assertThat(controller.highlights(1L).trendingProductIds()).isEmpty();
        verify(menu, times(2)).getMenu(1L);
    }

    @Test void coalescesConcurrentLoadsWithoutBlockingOtherBranches() throws Exception {
        var loading = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        when(availability.check(eq(1L), any(LocalDate.class), anyInt(), anyList())).thenAnswer(invocation -> {
            loading.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return result(true);
        });
        try (var executor = Executors.newFixedThreadPool(3)) {
            var first = executor.submit(() -> controller.highlights(1L));
            try {
                assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    return controller.highlights(1L);
                });
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(executor.submit(() -> controller.highlights(2L)).get(5, TimeUnit.SECONDS)
                        .trendingProductIds()).containsExactly(11L);
                release.countDown();
                assertThat(second.get(5, TimeUnit.SECONDS)).isSameAs(first.get(5, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
        verify(availability).check(eq(1L), any(LocalDate.class), anyInt(), anyList());
    }

    private CartAvailabilityService.Availability result(boolean available) {
        var today = LocalDate.ofInstant(now, ZoneId.of("Asia/Kolkata"));
        return new CartAvailabilityService.Availability("PICKUP", today, today.plusDays(30),
                List.of(new CartAvailabilityService.DateAvailability(today, available, List.of())));
    }
}
