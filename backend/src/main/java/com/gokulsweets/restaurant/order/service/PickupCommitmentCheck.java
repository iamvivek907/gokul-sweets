package com.gokulsweets.restaurant.order.service;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.order.dto.CreateOrderItemRequest;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.order.service.model.ValidatedOrderData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** A read-only preflight. The slot update and locked inventory holds remain the final atomic gate. */
@Service
@RequiredArgsConstructor
public class PickupCommitmentCheck {
    private final EnhancementProperties features;
    private final CartAvailabilityService availability;

    public void checkNewOrder(ValidatedOrderData order, List<CreateOrderItemRequest> items) {
        if (!features.isSmartAvailability() || !features.isAuthoritativePickupCommitment()) return;

        var slot = order.pickupSlot();
        var date = slot.getSlotDate();
        var result = availability.check(order.branch().getId(), date, 1, items);
        var day = result.dates().stream().filter(value -> value.date().equals(date)).findFirst()
                .orElseThrow(() -> conflict("No pickup options remain for this date.", date.toString()));
        var selected = day.slots().stream().filter(value -> value.slot().id().equals(slot.getId())).findFirst()
                .orElseThrow(() -> conflict("That pickup time is no longer available.", date.toString()));

        boolean allowed = switch (order.pickupType()) {
            case NORMAL -> selected.normalAvailable();
            case PRIORITY -> selected.priorityAvailable();
            case ADMIN_OVERRIDE -> false;
        };
        if (!allowed || !selected.issues().isEmpty()) {
            String reason = selected.reason() != null ? selected.reason()
                    : day.reason() != null ? day.reason() : "That pickup time cannot fit your whole cart.";
            throw conflict(reason, date.toString());
        }
    }

    private static InventoryConflictException conflict(String reason, String date) {
        return new InventoryConflictException("PICKUP_COMMITMENT_CHANGED", reason,
                Map.of("serviceDate", date));
    }
}
