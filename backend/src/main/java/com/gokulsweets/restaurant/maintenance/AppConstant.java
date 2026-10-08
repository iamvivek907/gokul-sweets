package com.gokulsweets.restaurant.maintenance;

/**
 * Compile-time constants used by the com.gokulsweets.restaurant.maintenance package. Existing
 * declarations retain aliases for compatibility.
 */
public final class AppConstant {

    private AppConstant() {}

    /** Original DataCleanupService.LIMIT value; unchanged during extraction. */
    public static final int DATA_CLEANUP_SERVICE_LIMIT = 500;

    /** Original DataCleanupService.CUSTOMER value; unchanged during extraction. */
    public static final String DATA_CLEANUP_SERVICE_CUSTOMER =
            """
FROM customer_notification_events e JOIN orders o ON o.order_number=e.target_id
WHERE e.environment=:environment AND e.target_type='ORDER'
  AND e.kind IN ('CONFIRMED','PREPARING','READY_FOR_PICKUP','READY_FOR_DELIVERY','OUT_FOR_DELIVERY','READY_TIME_CHANGED')
  AND e.created_at<:cutoff AND e.read_at<:cutoff
  AND o.order_status IN ('PICKED_UP','DELIVERED') AND o.updated_at<:localCutoff
  AND NOT EXISTS (SELECT 1 FROM customer_push_deliveries d WHERE d.event_id=e.id
    AND (d.state NOT IN ('ACCEPTED','REVOKED','SKIPPED','FAILED') OR d.lease_until>:now))
""";

    /** Original DataCleanupService.STAFF value; unchanged during extraction. */
    public static final String DATA_CLEANUP_SERVICE_STAFF =
            """
FROM staff_order_alerts e JOIN orders o ON o.id=e.order_id
WHERE e.environment=:environment
  AND e.kind IN ('NEW_ORDER','PREPARATION_SOON','PREPARATION_DUE','PREPARATION_OVERDUE','READY_OVERDUE')
  AND e.created_at<:cutoff AND (e.scheduled_at IS NULL OR e.scheduled_at<:localCutoff)
  AND o.order_status IN ('PICKED_UP','DELIVERED') AND o.updated_at<:localCutoff
  AND NOT EXISTS (SELECT 1 FROM staff_alert_deliveries d WHERE d.event_id=e.id
    AND (d.state NOT IN ('ACCEPTED','REVOKED','SKIPPED','FAILED') OR d.lease_until>:now))
""";
}
