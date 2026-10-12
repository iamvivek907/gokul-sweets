package com.gokulsweets.restaurant.printing;

import static org.assertj.core.api.Assertions.*;

import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import com.gokulsweets.restaurant.printing.service.PrintStationActionService;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
class PrintStationActionTest {
    @Autowired PrintStationActionService actions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StaffAuthorizationService authorization;
    Long branch;

    @BeforeEach
    void setup() {
        branch =
                jdbc.queryForObject(
                        "INSERT INTO branches(code,name) VALUES (?,?) RETURNING id",
                        Long.class,
                        "ACTION-" + UUID.randomUUID(),
                        "Action test");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM print_station_action_receipts WHERE branch_id=?", branch);
        jdbc.update("DELETE FROM branches WHERE id=?", branch);
    }

    @Test
    void reconciliationBeforeDispatchCancelsLateRequestAndNeverRunsIt() {
        var id = UUID.randomUUID();
        var calls = new AtomicInteger();
        assertThat(actions.reconcile(branch, PrinterStation.KITCHEN, id))
                .containsEntry(
                        "actionReceipt",
                        Map.of("requestId", id.toString(), "outcome", "CANCELLED"));
        var late =
                actions.perform(
                        branch,
                        PrinterStation.KITCHEN,
                        id,
                        () -> {
                            calls.incrementAndGet();
                            return Map.of();
                        });
        assertThat(late)
                .containsEntry(
                        "actionReceipt",
                        Map.of("requestId", id.toString(), "outcome", "CANCELLED"));
        assertThat(calls.get()).isZero();
    }

    @Test
    void reconciliationWaitsForAcceptanceAndDuplicateRequestsNeverReplay() throws Exception {
        var id = UUID.randomUUID();
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var original =
                    executor.submit(
                            () ->
                                    actions.perform(
                                            branch,
                                            PrinterStation.KITCHEN,
                                            id,
                                            () -> {
                                                calls.incrementAndGet();
                                                entered.countDown();
                                                try {
                                                    if (!release.await(5, TimeUnit.SECONDS))
                                                        throw new IllegalStateException(
                                                                "Test gate timed out");
                                                } catch (InterruptedException e) {
                                                    Thread.currentThread().interrupt();
                                                    throw new IllegalStateException(e);
                                                }
                                                return Map.of();
                                            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var reconciling =
                    executor.submit(() -> actions.reconcile(branch, PrinterStation.KITCHEN, id));
            try {
                assertThatThrownBy(() -> reconciling.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            original.get(5, TimeUnit.SECONDS);
            assertThat(reconciling.get(5, TimeUnit.SECONDS))
                    .containsEntry(
                            "actionReceipt",
                            Map.of("requestId", id.toString(), "outcome", "ACCEPTED"));
        } finally {
            release.countDown();
        }
        actions.perform(
                branch,
                PrinterStation.KITCHEN,
                id,
                () -> {
                    calls.incrementAndGet();
                    return Map.of();
                });
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void failedTransactionLeavesNoAcceptedReceipt() {
        var id = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                actions.perform(
                                        branch,
                                        PrinterStation.KITCHEN,
                                        id,
                                        () -> {
                                            throw new IllegalStateException("Rejected action");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(actions.reconcile(branch, PrinterStation.KITCHEN, id))
                .containsEntry(
                        "actionReceipt",
                        Map.of("requestId", id.toString(), "outcome", "CANCELLED"));
    }
}
