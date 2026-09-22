package com.gokulsweets.restaurant.order.service.model;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.pickup.PickupSlot;

import java.util.List;

public record ValidatedOrderData(
        Branch branch,
        PickupSlot pickupSlot,
        PickupType pickupType,
        List<ValidatedOrderItem> items
) {
}