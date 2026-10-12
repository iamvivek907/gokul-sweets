package com.gokulsweets.restaurant.menu;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class MenuCacheConcurrencyTest {
    @Test
    void cachedCatalogReadsOfSameBranchDoNotSerializeTheirRevisionQueries() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT active"), any(RowMapper.class), anyLong()))
                .thenReturn(List.of(new boolean[] {true, true}));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(jdbc.queryForObject(contains("SELECT token"), eq(String.class)))
                .thenAnswer(
                        call -> {
                            if (Thread.currentThread().getName().equals("slow-hit")) {
                                entered.countDown();
                                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            }
                            return "r1";
                        });
        var catalog = new MenuCatalogService(jdbc);
        var warmed = readOnly(() -> catalog.get(1));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var slow =
                    executor.submit(
                            () -> {
                                Thread.currentThread().setName("slow-hit");
                                return readOnly(() -> catalog.get(1));
                            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var other = executor.submit(() -> readOnly(() -> catalog.get(1)));
            try {
                assertThat(other.get(2, TimeUnit.SECONDS)).isSameAs(warmed);
            } finally {
                release.countDown();
            }
            assertThat(slow.get(5, TimeUnit.SECONDS)).isSameAs(warmed);
        } finally {
            release.countDown();
        }
    }

    @Test
    void cachedAvailabilityReadsDoNotHoldRebuildLockWhileCheckingCatalog() throws Exception {
        var catalog = mock(MenuCatalogService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(catalog.get(1L))
                .thenAnswer(
                        call -> {
                            if (Thread.currentThread().getName().equals("slow-hit")) {
                                entered.countDown();
                                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            }
                            return new MenuCatalogService.Catalog("r1", List.of());
                        });
        when(catalog.revision()).thenReturn("r1");
        var windows = mock(MenuServiceWindows.class);
        when(windows.pickupSnapshot(1L, null))
                .thenReturn(new MenuServiceWindows.Snapshot(false, java.util.Map.of()));
        var service =
                new MenuAvailabilityService(
                        catalog, windows, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var warmed = readOnly(() -> service.get(1));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var slow =
                    executor.submit(
                            () -> {
                                Thread.currentThread().setName("slow-hit");
                                return readOnly(() -> service.get(1));
                            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var other = executor.submit(() -> readOnly(() -> service.get(1)));
            try {
                assertThat(other.get(2, TimeUnit.SECONDS).items()).isSameAs(warmed.items());
            } finally {
                release.countDown();
            }
            assertThat(slow.get(5, TimeUnit.SECONDS).items()).isSameAs(warmed.items());
            verify(windows, times(1)).pickupSnapshot(1L, null);
        } finally {
            release.countDown();
        }
    }

    @Test
    void slowCatalogRebuildDoesNotBlockAnotherBranchAndSameBranchSharesSnapshot() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("SELECT active"), any(RowMapper.class), anyLong()))
                .thenReturn(List.of(new boolean[] {true, true}));
        when(jdbc.queryForObject(contains("SELECT token"), eq(String.class))).thenReturn("r1");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var builds = new AtomicInteger();
        doAnswer(
                        call -> {
                            if (call.<Long>getArgument(2) == 1L) {
                                builds.incrementAndGet();
                                entered.countDown();
                                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            }
                            return null;
                        })
                .when(jdbc)
                .query(contains("FROM branch_products"), any(RowCallbackHandler.class), anyLong());
        var catalog = new MenuCatalogService(jdbc);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var slow = executor.submit(() -> readOnly(() -> catalog.get(1)));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var same = executor.submit(() -> readOnly(() -> catalog.get(1)));
            var other = executor.submit(() -> readOnly(() -> catalog.get(2)));
            try {
                assertThat(other.get(2, TimeUnit.SECONDS).revision()).isEqualTo("r1");
            } finally {
                release.countDown();
            }
            assertThat(same.get(5, TimeUnit.SECONDS)).isSameAs(slow.get(5, TimeUnit.SECONDS));
            assertThat(builds.get()).isEqualTo(1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void slowAvailabilityRebuildDoesNotBlockAnotherBranchAndSameBranchSharesEvaluation()
            throws Exception {
        var catalog = mock(MenuCatalogService.class);
        when(catalog.get(anyLong())).thenReturn(new MenuCatalogService.Catalog("r1", List.of()));
        when(catalog.revision()).thenReturn("r1");
        var windows = mock(MenuServiceWindows.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(windows.pickupSnapshot(anyLong(), isNull()))
                .thenAnswer(
                        call -> {
                            if (call.<Long>getArgument(0) == 1L) {
                                entered.countDown();
                                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            }
                            return new MenuServiceWindows.Snapshot(false, java.util.Map.of());
                        });
        var service =
                new MenuAvailabilityService(
                        catalog, windows, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        try (var executor = Executors.newFixedThreadPool(3)) {
            var slow = executor.submit(() -> readOnly(() -> service.get(1)));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var same = executor.submit(() -> readOnly(() -> service.get(1)));
            var other = executor.submit(() -> readOnly(() -> service.get(2)));
            try {
                assertThat(other.get(2, TimeUnit.SECONDS).revision()).isEqualTo("r1");
            } finally {
                release.countDown();
            }
            assertThat(same.get(5, TimeUnit.SECONDS).items())
                    .isSameAs(slow.get(5, TimeUnit.SECONDS).items());
            verify(windows, times(1)).pickupSnapshot(1L, null);
        } finally {
            release.countDown();
        }
    }

    private static <T> T readOnly(Callable<T> read) throws Exception {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            return read.call();
        } finally {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        }
    }
}
