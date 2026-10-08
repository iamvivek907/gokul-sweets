package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** Immutable quote assumptions and current refund-adjusted contribution for finance review. */
@RestController
@RequiredArgsConstructor
public class AdminDeliveryEconomicsController {

    private final EnhancementProperties flags;

    private final JdbcTemplate jdbc;

    private final com.gokulsweets.restaurant.security.StaffAuthorizationService authorization;

    /**
     * Details the operation.
     *
     * @param orderId the order id
     * @return the detail result
     */
    @GetMapping("/api/admin/orders/{orderId}/delivery-economics")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable long orderId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminDeliveryEconomicsController.class, "detail(long)");
        try {
            if (!flags.isDeliveryEconomics()) return ResponseEntity.notFound().build();
            var results =
                    jdbc.query(
                            """
SELECT o.branch_id, o.order_number, o.subtotal, o.tax_amount, o.delivery_fee,
       s.version, s.food_cost, s.packaging_cost, s.labour_cost, s.waste_cost,
       s.payment_cost, s.journey_cost, s.remedy_cost, s.contribution,
       COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.order_id = o.id
           AND p.payment_status = 'REFUNDED'), 0) AS refunded
FROM orders o JOIN delivery_economics_snapshots s ON s.order_id = o.id WHERE o.id = ?
""",
                            (rs, index) -> {
                                BigDecimal refunded = rs.getBigDecimal("refunded");
                                return Map.<String, Object>ofEntries(
                                        Map.entry("branchId", rs.getLong("branch_id")),
                                        Map.entry("orderNumber", rs.getString("order_number")),
                                        Map.entry("version", rs.getString("version")),
                                        Map.entry("subtotal", rs.getBigDecimal("subtotal")),
                                        Map.entry("taxAmount", rs.getBigDecimal("tax_amount")),
                                        Map.entry("deliveryFee", rs.getBigDecimal("delivery_fee")),
                                        Map.entry("foodCost", rs.getBigDecimal("food_cost")),
                                        Map.entry(
                                                "packagingCost",
                                                rs.getBigDecimal("packaging_cost")),
                                        Map.entry("labourCost", rs.getBigDecimal("labour_cost")),
                                        Map.entry("wasteCost", rs.getBigDecimal("waste_cost")),
                                        Map.entry("paymentCost", rs.getBigDecimal("payment_cost")),
                                        Map.entry("journeyCost", rs.getBigDecimal("journey_cost")),
                                        Map.entry("remedyCost", rs.getBigDecimal("remedy_cost")),
                                        Map.entry(
                                                "quotedContribution",
                                                rs.getBigDecimal("contribution")),
                                        Map.entry("refundedAmount", refunded),
                                        Map.entry(
                                                "contributionAfterRefund",
                                                rs.getBigDecimal("contribution")
                                                        .subtract(refunded)));
                            },
                            orderId);
            if (results.isEmpty()) return ResponseEntity.notFound().build();
            authorization.requireBranchAccess(
                    ((Number) results.getFirst().get("branchId")).longValue());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(results.getFirst());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminDeliveryEconomicsController.class,
                    "detail(long)");
        }
    }
}
