package com.gokulsweets.restaurant.order.service.model;

import com.gokulsweets.restaurant.menu.MenuServiceWindows;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

/**
 * Request-local pickup validation and the same service-rule snapshot used to evaluate its slots.
 * Never retained in a shared cache or reused for a later checkout.
 *
 * @param items validated quantities in request order
 * @param serviceAt evaluator built from this request's branch and service-rule reads
 */
public record ValidatedPickupCart(
        List<ValidatedOrderItem> items,
        Function<LocalDateTime, MenuServiceWindows.Snapshot> serviceAt) {}
