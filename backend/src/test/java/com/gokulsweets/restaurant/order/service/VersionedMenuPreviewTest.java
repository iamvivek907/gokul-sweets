package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.*;
import java.util.List;

class VersionedMenuPreviewTest {
    final MenuInventoryVersion versions = mock(MenuInventoryVersion.class);
    final CartAvailabilityService service = mock(CartAvailabilityService.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final Clock clock = mock(Clock.class);
    final Instant now = Instant.parse("2026-10-22T08:00:00Z");
    final LocalDate date = LocalDate.of(2026, 10, 22);
    final List<CreateOrderItemRequest> items = List.of(new CreateOrderItemRequest(1L, 1, null));
    VersionedMenuPreview cache;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(now);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(versions.read(anyLong(), any())).thenReturn("v1");
        when(service.check(anyLong(), any(), eq(1), anyList(), eq(true)))
                .thenAnswer(call -> result(now.plusSeconds(90)));
        cache = new VersionedMenuPreview(versions, service, jdbc, clock, manager);
    }

    CartAvailabilityService.Availability result(Instant boundary) {
        return new CartAvailabilityService.Availability("PICKUP", date, date, List.of(), boundary);
    }

    @Test
    void unchangedVersionReusesCompletedPreviewButStillProbesDatabase() {
        var first = cache.check(1, date, items);
        assertThat(cache.check(1, date, items)).isSameAs(first);
        verify(service, times(1)).check(1L, date, 1, items, true);
        verify(versions, times(2)).read(1, date);
        var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager, times(2)).getTransaction(definition.capture());
        assertThat(definition.getValue().isReadOnly()).isTrue();
        assertThat(definition.getValue().getIsolationLevel())
                .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(definition.getValue().getTimeout()).isEqualTo(5);
    }

    @Test
    void committedChangeNeverReusesPreviousStock() {
        var first = cache.check(1, date, items);
        when(versions.read(1, date)).thenReturn("v2");
        assertThat(cache.check(1, date, items)).isNotSameAs(first);
        verify(service, times(2)).check(1L, date, 1, items, true);
    }

    @Test
    void branchDateQuantitiesWeightsAndOrderAreIndependent() {
        cache.check(1, date, items);
        cache.check(2, date, items);
        cache.check(1, date.plusDays(1), items);
        cache.check(1, date, List.of(new CreateOrderItemRequest(1L, 2, null)));
        cache.check(1, date, List.of(new CreateOrderItemRequest(1L, null, 250)));
        var two = List.of(items.getFirst(), new CreateOrderItemRequest(2L, 1, null));
        cache.check(1, date, two);
        cache.check(1, date, two.reversed());
        verify(service, times(7)).check(anyLong(), any(), eq(1), anyList(), eq(true));
    }

    @Test
    void preparationBoundaryAndClockReversalForceFreshReads() {
        cache.check(1, date, items);
        when(clock.instant()).thenReturn(now.plusSeconds(90));
        cache.check(1, date, items);
        when(clock.instant()).thenReturn(now.minusSeconds(1));
        cache.check(1, date, items);
        verify(service, times(3)).check(1L, date, 1, items, true);
    }

    @Test
    void missingBoundaryOrLongLivedVersionCannotCacheIndefinitely() {
        when(service.check(1L, date, 1, items, true)).thenReturn(result(null));
        cache.check(1, date, items);
        cache.check(1, date, items);
        verify(service, times(2)).check(1L, date, 1, items, true);
        when(service.check(1L, date, 1, items, true)).thenReturn(result(now.plusSeconds(3600)));
        cache.check(1, date, items);
        when(clock.instant()).thenReturn(now.plusSeconds(300));
        cache.check(1, date, items);
        verify(service, times(4)).check(1L, date, 1, items, true);
    }

    @Test
    void failedTransactionDoesNotPublishPreview() {
        doThrow(new org.springframework.transaction.TransactionTimedOutException("deadline"))
                .doNothing()
                .when(manager)
                .commit(any());
        assertThatThrownBy(() -> cache.check(1, date, items))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        cache.check(1, date, items);
        verify(service, times(2)).check(1L, date, 1, items, true);
    }

    @Test
    void entryCountIsBoundedAndEvictedRequestsReload() {
        cache.check(1, date, items);
        for (long id = 2; id <= 65; id++) cache.check(id, date, items);
        cache.check(1, date, items);
        verify(service, times(2)).check(1L, date, 1, items, true);
    }

    @Test
    void shortLivedStateHintsDoNotWeakenFreshPreviewValidation() {
        assertThat(cache.state(1, date)).isEqualTo("v1");
        when(versions.read(1, date)).thenReturn("v2");
        assertThat(cache.state(1, date)).isEqualTo("v1");
        cache.check(1, date, items);
        verify(versions, times(2)).read(1, date);
        when(clock.instant()).thenReturn(now.plusSeconds(1));
        assertThat(cache.state(1, date)).isEqualTo("v2");
    }
}
