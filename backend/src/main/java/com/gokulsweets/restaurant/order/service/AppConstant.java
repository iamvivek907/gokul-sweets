package com.gokulsweets.restaurant.order.service;

/**
 * Compile-time constants used by the com.gokulsweets.restaurant.order.service package. Existing
 * declarations retain aliases for compatibility.
 */
public final class AppConstant {

    /** Maximum number of distinct in-progress public preview batches retained at once. */
    public static final int MENU_PREVIEW_MAX_PENDING = 64;

    /** Maximum age at which another request can join an unfinished preview; never a result TTL. */
    public static final int MENU_PREVIEW_JOIN_MILLIS = 250;

    /** Creates a app constant instance. */
    private AppConstant() {}

    /**
     * Original AdminOrderBatchPreparationService.MAX_BATCH_SIZE value; unchanged during extraction.
     */
    public static final int ADMIN_ORDER_BATCH_PREPARATION_SERVICE_MAX_BATCH_SIZE = 50;

    /** Original AdminOrderQueryService.MAX_PAGE_SIZE value; unchanged during extraction. */
    public static final int ADMIN_ORDER_QUERY_SERVICE_MAX_PAGE_SIZE = 100;

    /** Original AdminOrderQueryService.DEFAULT_QUEUE_LIMIT value; unchanged during extraction. */
    public static final int ADMIN_ORDER_QUERY_SERVICE_DEFAULT_QUEUE_LIMIT = 50;

    /** Original AdminOrderQueryService.MAX_QUEUE_LIMIT value; unchanged during extraction. */
    public static final int ADMIN_ORDER_QUERY_SERVICE_MAX_QUEUE_LIMIT = 200;

    /** Original KitchenPlanningService.BASE value; unchanged during extraction. */
    public static final String KITCHEN_PLANNING_SERVICE_BASE =
            """
WITH timed AS (
  SELECT o.id,o.order_number,o.customer_order_number,o.customer_name,o.fulfillment_type,o.order_status,
   (o.fulfillment_type<>'DELIVERY' AND EXISTS(SELECT 1 FROM order_items i WHERE i.order_id=o.id)
     AND NOT EXISTS(SELECT 1 FROM order_items i LEFT JOIN branch_products bp ON bp.branch_id=o.branch_id AND bp.product_id=i.product_id
        WHERE i.order_id=o.id AND NOT COALESCE(bp.early_preparation_allowed,FALSE))) early_preparation,
   CASE WHEN o.fulfillment_type='DELIVERY' THEN w.service_date ELSE s.slot_date END service_date,
   CASE WHEN o.fulfillment_type='DELIVERY' THEN w.starts_at ELSE s.start_time END starts_at,
   CASE WHEN o.fulfillment_type='DELIVERY' THEN w.ends_at ELSE s.end_time END ends_at,
   CASE WHEN o.fulfillment_type='DELIVERY' THEN ? WHEN o.pickup_type='PRIORITY' THEN ?
        WHEN o.pickup_type='ADMIN_OVERRIDE' THEN ? ELSE ? END lead
  FROM orders o LEFT JOIN pickup_slots s ON s.id=o.pickup_slot_id
  LEFT JOIN delivery_capacity_windows w ON w.id=o.delivery_window_id
  LEFT JOIN delivery_zones z ON z.id=w.zone_id AND z.branch_id=o.branch_id
  WHERE o.branch_id=? AND o.order_status IN ('CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY')
    AND (o.fulfillment_type<>'DELIVERY' OR z.id IS NOT NULL)
), planned AS (
  SELECT *, CASE WHEN early_preparation THEN LEAST(service_date::timestamp,service_date+starts_at-(lead*INTERVAL '1 minute'))
    ELSE service_date+starts_at-(lead*INTERVAL '1 minute') END preparation_at,
    CASE WHEN order_status IN ('READY_FOR_PICKUP','READY_FOR_DELIVERY') THEN 'READY'
    WHEN service_date+starts_at<=? THEN 'OVERDUE'
    WHEN order_status='PREPARING' THEN 'PREPARING'
    WHEN CASE WHEN early_preparation THEN LEAST(service_date::timestamp,service_date+starts_at-(lead*INTERVAL '1 minute')) ELSE service_date+starts_at-(lead*INTERVAL '1 minute') END<=? THEN 'ELIGIBLE' ELSE 'SCHEDULED' END bucket
  FROM timed WHERE service_date IS NOT NULL AND starts_at IS NOT NULL
)
""";

    /** Original OrderCalculationService.MONEY_SCALE value; unchanged during extraction. */
    public static final int ORDER_CALCULATION_SERVICE_MONEY_SCALE = 2;

    /** Original OrderDemandService.LINES value; unchanged during extraction. */
    public static final String ORDER_DEMAND_SERVICE_LINES =
            """
SELECT o.order_number,o.customer_order_number,o.order_status,
 CASE WHEN o.fulfillment_type='DELIVERY' THEN w.service_date ELSE s.slot_date END service_date,
 CASE WHEN o.fulfillment_type='DELIVERY' THEN w.starts_at ELSE s.start_time END pickup_time,
 i.product_id,i.product_name,i.sale_mode,
 CASE WHEN i.sale_mode='WEIGHT' THEN i.weight_grams ELSE i.quantity END amount
FROM orders o JOIN order_items i ON i.order_id=o.id
 LEFT JOIN pickup_slots s ON s.id=o.pickup_slot_id AND s.branch_id=o.branch_id
 LEFT JOIN delivery_capacity_windows w ON w.id=o.delivery_window_id
 LEFT JOIN delivery_zones z ON z.id=w.zone_id AND z.branch_id=o.branch_id
WHERE o.branch_id=? AND o.order_status IN
 ('CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY','PICKED_UP','OUT_FOR_DELIVERY','DELIVERED')
 AND (o.fulfillment_type<>'DELIVERY' OR z.id IS NOT NULL)
""";

    /** Original OrderQueryService.MAX_HISTORY_ORDERS value; unchanged during extraction. */
    public static final int ORDER_QUERY_SERVICE_MAX_HISTORY_ORDERS = 500;
}
