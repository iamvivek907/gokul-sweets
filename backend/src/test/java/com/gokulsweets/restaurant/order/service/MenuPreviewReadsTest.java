package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

class MenuPreviewReadsTest {
    private final LocalDate date = LocalDate.of(2026, 10, 10);
    private final List<CreateOrderItemRequest> items =
            List.of(new CreateOrderItemRequest(1L, 1, null));
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-09T06:00:00Z"), ZoneOffset.UTC);

    @Test
    void overlappingIdenticalReadsShareWorkButCompletedResultsAreNeverCached() throws Exception {
        var service = mock(CartAvailabilityService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var firstResult = new CartAvailabilityService.Availability("PICKUP", date, date, List.of());
        var secondResult =
                new CartAvailabilityService.Availability(
                        "PICKUP", date, date.plusDays(1), List.of());
        when(service.check(1L, date, 1, items, true))
                .thenAnswer(
                        call -> {
                            entered.countDown();
                            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            return firstResult;
                        })
                .thenReturn(secondResult);
        var reads = new MenuPreviewReads(service, clock, () -> 0L);
        var owner = CompletableFuture.supplyAsync(() -> reads.check(1L, date, items));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var followerResult = new AtomicReference<CartAvailabilityService.Availability>();
        var follower = new Thread(() -> followerResult.set(reads.check(1L, date, items)));
        try {
            follower.start();
            awaitWaiting(follower);
            verify(service, times(1)).check(1L, date, 1, items, true);
        } finally {
            release.countDown();
        }
        assertThat(owner.get(5, TimeUnit.SECONDS)).isSameAs(firstResult);
        follower.join(5000);
        assertThat(follower.isAlive()).isFalse();
        assertThat(followerResult.get()).isSameAs(firstResult);
        assertThat(reads.check(1L, date, items)).isSameAs(secondResult);
        verify(service, times(2)).check(1L, date, 1, items, true);
    }

    @Test
    void branchDateQuantityWeightAndItemOrderAreNeverMixed() throws Exception {
        var service = mock(CartAvailabilityService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var result = new CartAvailabilityService.Availability("PICKUP", date, date, List.of());
        when(service.check(anyLong(), any(), eq(1), anyList(), eq(true)))
                .thenAnswer(
                        call -> {
                            if (call.getArgument(0).equals(1L)
                                    && call.getArgument(1).equals(date)
                                    && call.getArgument(3).equals(items)) {
                                entered.countDown();
                                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            }
                            return result;
                        });
        var reads = new MenuPreviewReads(service, clock, () -> 0L);
        var owner = CompletableFuture.supplyAsync(() -> reads.check(1L, date, items));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            reads.check(2L, date, items);
            reads.check(1L, date.plusDays(1), items);
            reads.check(1L, date, List.of(new CreateOrderItemRequest(1L, 2, null)));
            reads.check(1L, date, List.of(new CreateOrderItemRequest(1L, 1, 500)));
            reads.check(
                    1L, date, List.of(items.getFirst(), new CreateOrderItemRequest(2L, 1, null)));
            reads.check(
                    1L, date, List.of(new CreateOrderItemRequest(2L, 1, null), items.getFirst()));
            verify(service, times(7)).check(anyLong(), any(), eq(1), anyList(), eq(true));
        } finally {
            release.countDown();
        }
        owner.get(5, TimeUnit.SECONDS);
    }

    @Test
    void oldPendingReadsAreNotJoined() throws Exception {
        var service = mock(CartAvailabilityService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ticks = new AtomicLong();
        var result = new CartAvailabilityService.Availability("PICKUP", date, date, List.of());
        when(service.check(1L, date, 1, items, true))
                .thenAnswer(
                        call -> {
                            entered.countDown();
                            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            return result;
                        })
                .thenReturn(result);
        var reads = new MenuPreviewReads(service, clock, ticks::get);
        var owner = CompletableFuture.supplyAsync(() -> reads.check(1L, date, items));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            ticks.set(TimeUnit.MILLISECONDS.toNanos(AppConstant.MENU_PREVIEW_JOIN_MILLIS + 1));
            assertThat(reads.check(1L, date, items)).isSameAs(result);
            verify(service, times(2)).check(1L, date, 1, items, true);
        } finally {
            release.countDown();
        }
        owner.get(5, TimeUnit.SECONDS);
    }

    @Test
    void crossingAClockBoundaryReadsAgainEvenWhileTheEarlierReadIsPending() throws Exception {
        var service = mock(CartAvailabilityService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var movingClock = mock(Clock.class);
        when(movingClock.instant()).thenReturn(clock.instant());
        var result = new CartAvailabilityService.Availability("PICKUP", date, date, List.of());
        when(service.check(1L, date, 1, items, true))
                .thenAnswer(
                        call -> {
                            entered.countDown();
                            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            return result;
                        })
                .thenReturn(result);
        var reads = new MenuPreviewReads(service, movingClock, () -> 0L);
        var owner = CompletableFuture.supplyAsync(() -> reads.check(1L, date, items));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            when(movingClock.instant()).thenReturn(clock.instant().plusSeconds(1));
            assertThat(reads.check(1L, date, items)).isSameAs(result);
            verify(service, times(2)).check(1L, date, 1, items, true);
        } finally {
            release.countDown();
        }
        owner.get(5, TimeUnit.SECONDS);
    }

    @Test
    void failedReadsPreserveOriginalExceptionAndDoNotPoisonLaterRequests() throws Exception {
        var service = mock(CartAvailabilityService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failure = new IllegalArgumentException("Branch closed");
        var result = new CartAvailabilityService.Availability("PICKUP", date, date, List.of());
        when(service.check(1L, date, 1, items, true))
                .thenAnswer(
                        call -> {
                            entered.countDown();
                            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                            throw failure;
                        })
                .thenReturn(result);
        var reads = new MenuPreviewReads(service, clock, () -> 0L);
        var owner = CompletableFuture.supplyAsync(() -> reads.check(1L, date, items));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var followerFailure = new AtomicReference<Throwable>();
        var follower =
                new Thread(
                        () -> {
                            try {
                                reads.check(1L, date, items);
                            } catch (Throwable thrown) {
                                followerFailure.set(thrown);
                            }
                        });
        try {
            follower.start();
            awaitWaiting(follower);
        } finally {
            release.countDown();
        }
        assertThatThrownBy(() -> owner.get(5, TimeUnit.SECONDS)).hasCause(failure);
        follower.join(5000);
        assertThat(follower.isAlive()).isFalse();
        assertThat(followerFailure.get()).isSameAs(failure);
        assertThat(reads.check(1L, date, items)).isSameAs(result);
    }

    @Test
    void saturatedRegistryFallsBackToFreshReadsWithoutGrowingOrBlockingOtherKeys()
            throws Exception {
        var service = mock(CartAvailabilityService.class);
        var firstOwners = new CountDownLatch(AppConstant.MENU_PREVIEW_MAX_PENDING);
        var overflowReads = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var result = new CartAvailabilityService.Availability("PICKUP", date, date, List.of());
        when(service.check(anyLong(), any(), eq(1), anyList(), eq(true)))
                .thenAnswer(
                        call -> {
                            if ((Long) call.getArgument(0) <= AppConstant.MENU_PREVIEW_MAX_PENDING)
                                firstOwners.countDown();
                            else overflowReads.countDown();
                            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                            return result;
                        });
        var reads = new MenuPreviewReads(service, clock, () -> 0L);
        var workers = new java.util.ArrayList<Thread>();
        try {
            for (long id = 1; id <= AppConstant.MENU_PREVIEW_MAX_PENDING; id++) {
                long branch = id;
                workers.add(Thread.ofVirtual().start(() -> reads.check(branch, date, items)));
            }
            assertThat(firstOwners.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 2; i++)
                workers.add(Thread.ofVirtual().start(() -> reads.check(100L, date, items)));
            assertThat(overflowReads.await(5, TimeUnit.SECONDS)).isTrue();
            var follower = new Thread(() -> reads.check(1L, date, items));
            workers.add(follower);
            follower.start();
            awaitWaiting(follower);
            verify(service, times(1)).check(1L, date, 1, items, true);
            verify(service, times(2)).check(100L, date, 1, items, true);
        } finally {
            release.countDown();
            for (var worker : workers) worker.join(5000);
        }
        assertThat(workers).allMatch(worker -> !worker.isAlive());
    }

    private void awaitWaiting(Thread follower) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (follower.getState() != Thread.State.WAITING
                && follower.isAlive()
                && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertThat(follower.getState()).isEqualTo(Thread.State.WAITING);
    }
}
