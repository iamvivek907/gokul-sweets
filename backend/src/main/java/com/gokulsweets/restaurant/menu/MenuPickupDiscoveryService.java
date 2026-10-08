package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.service.CartAvailabilityService.*;
import com.gokulsweets.restaurant.order.service.SmartOrderingRules;
import com.gokulsweets.restaurant.pickup.BranchPickupSettingsRepository;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;
import com.gokulsweets.restaurant.pickup.repository.PickupSlotRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Coordinates menu pickup discovery operations. */
@Service
@RequiredArgsConstructor
public class MenuPickupDiscoveryService {

    private final MenuCatalogService catalog;

    private final EnhancementProperties features;

    private final BranchPickupSettingsRepository settings;

    private final PickupSlotRepository slots;

    private final SmartOrderingRules rules;

    private final Clock inventoryClock;

    /** Branch capacity only. Item stock/preparation must be checked after choosing a date. */
    @Transactional(readOnly = true)
    public Availability discover(long branchId, LocalDate startDate, int days) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuPickupDiscoveryService.class, "discover(long,LocalDate,int)");
        try {
            catalog.requireBranch(branchId);
            LocalDate today = LocalDate.now(inventoryClock);
            if (startDate.isBefore(today)
                    || startDate.isAfter(today.plusDays(features.getFutureOrderingDays()))
                    || days < 1
                    || days > 61)
                throw new IllegalArgumentException(
                        "Choose a date within the advance ordering window.");
            var policy = settings.findByBranchId(branchId).orElse(null);
            LocalDate maximum =
                    today.plusDays(
                            policy == null
                                    ? features.getFutureOrderingDays()
                                    : Math.min(
                                            features.getFutureOrderingDays(),
                                            policy.getAdvanceBookingDays()));
            if (startDate.isAfter(maximum))
                throw new IllegalArgumentException(
                        "Choose a date within the branch's advance ordering window.");
            LocalDate end = startDate.plusDays(days - 1L);
            if (end.isAfter(maximum)) end = maximum;
            var byDate =
                    slots
                            .findByBranchIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
                                    branchId, startDate, end)
                            .stream()
                            .collect(Collectors.groupingBy(s -> s.getSlotDate()));
            var dates = new ArrayList<DateAvailability>();
            for (LocalDate date = startDate; !date.isAfter(end); date = date.plusDays(1)) {
                var options =
                        byDate.getOrDefault(date, List.of()).stream()
                                .map(
                                        s -> {
                                            String reason = rules.windowReason(s, policy);
                                            boolean normal =
                                                    reason == null
                                                            && s.getBookedCount() < s.getCapacity();
                                            boolean priority =
                                                    reason == null
                                                            && s.isPriorityEnabled()
                                                            && s.getPriorityBookedCount()
                                                                    < s.getPriorityCapacity();
                                            return new SlotAvailability(
                                                    PickupSlotResponse.from(s),
                                                    normal,
                                                    priority,
                                                    reason,
                                                    reason == null ? null : "PICKUP_WINDOW",
                                                    List.of());
                                        })
                                .toList();
                boolean available =
                        options.stream()
                                .anyMatch(s -> s.normalAvailable() || s.priorityAvailable());
                dates.add(
                        new DateAvailability(
                                date,
                                available,
                                options,
                                List.of(),
                                available ? null : "No pickup times are open for this date.",
                                false));
            }
            return new Availability("PICKUP", today, maximum, List.copyOf(dates));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuPickupDiscoveryService.class,
                    "discover(long,LocalDate,int)");
        }
    }
}
