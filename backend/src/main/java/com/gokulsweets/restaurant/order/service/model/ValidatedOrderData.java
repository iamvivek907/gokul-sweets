package com.gokulsweets.restaurant.order.service.model;

import com.gokulsweets.restaurant.branch.Branch;
import com.gokulsweets.restaurant.order.enums.PickupType;
import com.gokulsweets.restaurant.pickup.PickupSlot;

import java.util.List;

/**
 * Immutable validated order data data contract.
 *
 * @param branch the branch
 * @param pickupSlot the pickup slot
 * @param pickupType the pickup type
 * @param items the items
 */
public record ValidatedOrderData(
        Branch branch,
        PickupSlot pickupSlot,
        PickupType pickupType,
        List<ValidatedOrderItem> items) {}
