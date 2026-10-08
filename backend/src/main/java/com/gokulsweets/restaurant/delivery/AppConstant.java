package com.gokulsweets.restaurant.delivery;

/**
 * Compile-time constants used by the com.gokulsweets.restaurant.delivery package. Existing
 * declarations retain aliases for compatibility.
 */
public final class AppConstant {

    private AppConstant() {}

    /** Original DeliveryPreparationQueue.FROM value; unchanged during extraction. */
    public static final String DELIVERY_PREPARATION_QUEUE_FROM =
            """
            FROM orders o
            JOIN delivery_capacity_windows w ON w.id = o.delivery_window_id
            JOIN delivery_zones z ON z.id = w.zone_id AND z.branch_id = o.branch_id
            WHERE o.branch_id = ? AND o.fulfillment_type = 'DELIVERY'
              AND o.order_status = 'CONFIRMED'
            """;
}
