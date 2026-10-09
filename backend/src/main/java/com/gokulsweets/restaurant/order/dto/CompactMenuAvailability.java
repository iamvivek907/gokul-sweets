package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.order.service.CartAvailabilityService;
import com.gokulsweets.restaurant.order.service.CartAvailabilityService.ItemAvailability;
import com.gokulsweets.restaurant.pickup.dto.PickupSlotResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opt-in menu preview transport. Identical slot issues are sent once, without changing decisions.
 *
 * @param fulfilmentType original fulfilment type
 * @param today original service date
 * @param maximumDate original ordering limit
 * @param dates original dated decisions with indexed slot issues
 * @param issueCatalog distinct issues in first-occurrence order, scoped to this response only
 */
public record CompactMenuAvailability(
        String fulfilmentType,
        LocalDate today,
        LocalDate maximumDate,
        List<Date> dates,
        List<ItemAvailability> issueCatalog) {

    /**
     * Encodes the exact full preview with response-local indexes; stock and validation are
     * unchanged.
     *
     * @param source fully evaluated availability
     * @return compact preview preserving every issue and its slot order
     */
    public static CompactMenuAvailability from(CartAvailabilityService.Availability source) {
        long started =
                MethodTiming.start(
                        CompactMenuAvailability.class,
                        "from(CartAvailabilityService.Availability)");
        try {
            Map<ItemAvailability, Integer> indexes = new LinkedHashMap<>();
            List<Date> dates = new ArrayList<>();
            for (var date : source.dates()) {
                List<Slot> slots = new ArrayList<>();
                for (var slot : date.slots()) {
                    List<Integer> issues = new ArrayList<>();
                    for (var issue : slot.issues()) {
                        Integer index = indexes.get(issue);
                        if (index == null) {
                            index = indexes.size();
                            indexes.put(issue, index);
                        }
                        issues.add(index);
                    }
                    slots.add(
                            new Slot(
                                    slot.slot(),
                                    slot.normalAvailable(),
                                    slot.priorityAvailable(),
                                    slot.reason(),
                                    slot.code(),
                                    List.copyOf(issues)));
                }
                dates.add(
                        new Date(
                                date.date(),
                                date.available(),
                                List.copyOf(slots),
                                date.items(),
                                date.reason(),
                                date.plannedProduction()));
            }
            return new CompactMenuAvailability(
                    source.fulfilmentType(),
                    source.today(),
                    source.maximumDate(),
                    List.copyOf(dates),
                    List.copyOf(indexes.keySet()));
        } finally {
            MethodTiming.finish(
                    started,
                    CompactMenuAvailability.class,
                    "from(CartAvailabilityService.Availability)");
        }
    }

    /**
     * Dated decisions with compact slot issues.
     *
     * @param date pickup date
     * @param available whole preview availability
     * @param slots original slots with indexed issues
     * @param items original item summaries
     * @param reason original date reason
     * @param plannedProduction original production flag
     */
    public record Date(
            LocalDate date,
            boolean available,
            List<Slot> slots,
            List<ItemAvailability> items,
            String reason,
            boolean plannedProduction) {}

    /**
     * Slot decision with indexes into the enclosing response's issue catalog.
     *
     * @param slot original slot metadata
     * @param normalAvailable normal pickup decision
     * @param priorityAvailable priority pickup decision
     * @param reason original reason
     * @param code original reason code
     * @param issueIndexes issue indexes in original order, including any duplicates
     */
    public record Slot(
            PickupSlotResponse slot,
            boolean normalAvailable,
            boolean priorityAvailable,
            String reason,
            String code,
            List<Integer> issueIndexes) {}
}
