package com.gokulsweets.restaurant.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gokulsweets.restaurant.order.service.CartAvailabilityService.*;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class PickupSummaryTest {
    @Test
    void indexedSummaryMatchesOriginalAcrossStockFailuresWindowsCapacityAndIssueOrdering() {
        Random random = new Random(194);
        for (int scenario = 0; scenario < 400; scenario++) {
            List<ItemAvailability> stock = new ArrayList<>();
            for (long id = 1; id <= 100; id++) stock.add(item(id, random.nextBoolean()));
            List<SlotAvailability> slots = new ArrayList<>();
            for (int index = 0; index < random.nextInt(20); index++) {
                List<ItemAvailability> issues = new ArrayList<>();
                for (var item : stock)
                    if (random.nextBoolean()) {
                        var issue = item.unavailable("SERVICE_" + index, "Service reason " + index);
                        issues.add(issue);
                        if (random.nextBoolean())
                            issues.add(issue); // Duplicate issues count only once per slot.
                    }
                slots.add(
                        slot(
                                index,
                                random.nextBoolean(),
                                random.nextBoolean(),
                                random.nextBoolean(),
                                issues));
            }
            assertThat(CartAvailabilityService.summarizeDateItems(stock, slots))
                    .as("scenario %s", scenario)
                    .isEqualTo(original(stock, slots));
        }
    }

    @Test
    void largeUnavailableMenuRetainsExactReasonsAndReportsLocalCpuComparison() {
        List<ItemAvailability> stock = new ArrayList<>();
        for (long id = 1; id <= 100; id++) stock.add(item(id, true));
        List<SlotAvailability> slots = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            int at = index;
            slots.add(
                    slot(
                            index,
                            true,
                            false,
                            false,
                            stock.stream()
                                    .map(
                                            item ->
                                                    item.unavailable(
                                                            "NOT_READY", "Ready after slot " + at))
                                    .toList()));
        }
        var expected = original(stock, slots);
        for (int warm = 0; warm < 5; warm++) {
            original(stock, slots);
            CartAvailabilityService.summarizeDateItems(stock, slots);
        }
        long start = System.nanoTime();
        for (int run = 0; run < 20; run++) assertThat(original(stock, slots)).isEqualTo(expected);
        long oldNanos = System.nanoTime() - start;
        start = System.nanoTime();
        for (int run = 0; run < 20; run++)
            assertThat(CartAvailabilityService.summarizeDateItems(stock, slots))
                    .isEqualTo(expected);
        long newNanos = System.nanoTime() - start;
        System.out.printf(
                "Local CPU comparison (100 items, 40 slots, 20 summaries): original=%.2fms"
                        + " indexed=%.2fms%n",
                oldNanos / 1_000_000.0, newNanos / 1_000_000.0);
        // No timing assertion: this is not a database, gateway or production capacity benchmark.
    }

    private ItemAvailability item(long id, boolean available) {
        return new ItemAvailability(
                id,
                "Product " + id,
                "PIECE",
                BigDecimal.ONE,
                BigDecimal.TEN,
                available,
                available ? null : "NO_ALLOCATION",
                available ? null : "Stock not ready",
                null);
    }

    private SlotAvailability slot(
            int index,
            boolean normalCapacity,
            boolean priorityCapacity,
            boolean outsideWindow,
            List<ItemAvailability> issues) {
        return new SlotAvailability(
                new PickupSlotResponse(
                        (long) index,
                        1L,
                        LocalDate.of(2026, 10, 9),
                        LocalTime.NOON,
                        LocalTime.of(12, 30),
                        10,
                        normalCapacity ? 0 : 10,
                        normalCapacity ? 10 : 0,
                        true,
                        priorityCapacity,
                        5,
                        0,
                        priorityCapacity ? 5 : 0,
                        BigDecimal.TEN),
                false,
                false,
                null,
                outsideWindow ? "PICKUP_WINDOW" : null,
                issues);
    }

    // Preserve the pre-optimisation implementation as an independent behavioural oracle.
    private List<ItemAvailability> original(
            List<ItemAvailability> stock, List<SlotAvailability> slots) {
        var inWindow = slots.stream().filter(s -> !"PICKUP_WINDOW".equals(s.code())).toList();
        var open =
                inWindow.stream()
                        .filter(
                                s ->
                                        s.slot().remainingCapacity() > 0
                                                || s.slot().priorityEnabled()
                                                        && s.slot().priorityRemainingCapacity() > 0)
                        .toList();
        return stock.stream()
                .map(
                        item -> {
                            if (!item.available()) return item;
                            if (open.stream()
                                    .anyMatch(
                                            s ->
                                                    s.issues().stream()
                                                            .noneMatch(
                                                                    i ->
                                                                            i.productId()
                                                                                    .equals(
                                                                                            item
                                                                                                    .productId()))))
                                return item;
                            return open.stream()
                                    .flatMap(s -> s.issues().stream())
                                    .filter(i -> i.productId().equals(item.productId()))
                                    .findFirst()
                                    .orElse(
                                            inWindow.isEmpty()
                                                    ? item.unavailable(
                                                            "NO_SLOTS",
                                                            "No pickup times are open for this"
                                                                    + " date. Choose another date.")
                                                    : item.unavailable(
                                                            "SLOT_FULL",
                                                            "All pickup times are fully booked."
                                                                    + " Choose another date."));
                        })
                .toList();
    }
}
