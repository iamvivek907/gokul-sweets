package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.Map;

/** HTTP endpoints for operations today operations. */
@RestController
@RequiredArgsConstructor
public class OperationsTodayController {

    private final JdbcTemplate jdbc;

    private final StaffAuthorizationService staff;

    private final Clock clock;

    /**
     * Handles {@code GET /api/admin/branches/{branchId}/dashboard/today} for operations today.
     *
     * <p>Reads {@code delivery_capacity_windows}, {@code orders}, {@code pickup_slots}.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code result}
     */
    @GetMapping("/api/admin/branches/{branchId}/dashboard/today")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    @Transactional(readOnly = true)
    public Map<String, Object> today(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(OperationsTodayController.class, "today(long)");
        try {
            staff.requireBranchAccess(branchId);
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
            var result =
                    jdbc.queryForMap(
                            """
SELECT COUNT(*) FILTER(WHERE o.order_status IN ('CONFIRMED','PREPARING','READY_FOR_PICKUP','PICKED_UP','READY_FOR_DELIVERY','OUT_FOR_DELIVERY','DELIVERED','NO_SHOW','PICKUP_WINDOW_EXPIRED')) AS orders,
COUNT(*) FILTER(WHERE o.order_status='PREPARING') AS preparing,
COUNT(*) FILTER(WHERE o.order_status='READY_FOR_PICKUP') AS ready,
COUNT(*) FILTER(WHERE o.order_status IN ('PICKED_UP','DELIVERED')) AS completed
FROM orders o LEFT JOIN pickup_slots ps ON ps.id=o.pickup_slot_id
LEFT JOIN delivery_capacity_windows dw ON dw.id=o.delivery_window_id
WHERE o.branch_id=? AND COALESCE(ps.slot_date,dw.service_date)=?
""",
                            branchId,
                            java.sql.Date.valueOf(today));
            result.put("today", today.toString());
            result.put("updatedAt", clock.instant().toString());
            return result;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, OperationsTodayController.class, "today(long)");
        }
    }
}
