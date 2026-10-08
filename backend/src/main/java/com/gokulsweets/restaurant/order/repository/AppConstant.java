package com.gokulsweets.restaurant.order.repository;

/**
 * Compile-time constants used by the com.gokulsweets.restaurant.order.repository package. Existing
 * declarations retain aliases for compatibility.
 */
public final class AppConstant {

    private AppConstant() {}

    /** Original PreparationQueueQueriesImpl.SCOPE value; unchanged during extraction. */
    public static final String PREPARATION_QUEUE_QUERIES_IMPL_SCOPE =
            """
FROM orders o JOIN pickup_slots ps ON ps.id=o.pickup_slot_id
WHERE o.branch_id=? AND o.order_status=? AND (
    (o.pickup_type=? AND (ps.slot_date<? OR (ps.slot_date=? AND ps.start_time<=?)))
    OR (o.pickup_type=? AND (ps.slot_date<? OR (ps.slot_date=? AND ps.start_time<=?)))
    OR (o.pickup_type=? AND (ps.slot_date<? OR (ps.slot_date=? AND ps.start_time<=?)))
    OR (ps.slot_date=? AND EXISTS(SELECT 1 FROM order_items i WHERE i.order_id=o.id)
        AND NOT EXISTS(SELECT 1 FROM order_items i
            LEFT JOIN branch_products bp ON bp.branch_id=o.branch_id AND bp.product_id=i.product_id
            WHERE i.order_id=o.id AND NOT COALESCE(bp.early_preparation_allowed,FALSE)))
)
""";
}
