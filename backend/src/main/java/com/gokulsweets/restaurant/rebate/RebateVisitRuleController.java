package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** HTTP endpoints for rebate visit rule operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/rebates/visit-rules")
public class RebateVisitRuleController {

    private final StaffAuthorizationService staff;

    private final JdbcTemplate jdbc;

    /**
     * Immutable input data contract.
     *
     * @param minimumCompletedOrders the minimum completed orders
     * @param reason the reason
     */
    public record Input(int minimumCompletedOrders, String reason) {}

    /**
     * Handles {@code GET /api/admin/rebates/visit-rules} for rebate visit rule.
     *
     * <p>Authorization checks include {@code PermissionName.REBATE_VIEW}.
     *
     * <p>Reads {@code rebate_visit_rules}, {@code rebates}.
     *
     * @return the {@code ResponseEntity<List<Map<String, Object>>>} result
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateVisitRuleController.class, "list()");
        try {
            staff.requirePermission(PermissionName.REBATE_VIEW);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            jdbc
                                    .queryForList(
                                            "SELECT"
                                                + " r.id,r.code,r.name,r.branch_id,COALESCE(v.minimum_completed_orders,0)"
                                                + " AS minimum_completed_orders FROM rebates r LEFT"
                                                + " JOIN rebate_visit_rules v ON v.rebate_id=r.id"
                                                + " ORDER BY r.code")
                                    .stream()
                                    .filter(
                                            r ->
                                                    r.get("branch_id") == null
                                                            || visible(
                                                                    ((Number) r.get("branch_id"))
                                                                            .longValue()))
                                    .toList());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateVisitRuleController.class, "list()");
        }
    }

    /**
     * Returns visible information for rebate visit rule.
     *
     * @param id the id supplied to this method
     * @return the {@code boolean} result
     */
    private boolean visible(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateVisitRuleController.class, "visible(long)");
        try {
            try {
                staff.requireBranchAccess(id);
                return true;
            } catch (org.springframework.security.access.AccessDeniedException ignored) {
                return false;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateVisitRuleController.class, "visible(long)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/rebates/visit-rules/{id}} for rebate visit rule.
     *
     * <p>Authorization checks include {@code PermissionName.REBATE_MANAGE}.
     *
     * <p>Reads {@code rebates}.
     *
     * <p>Writes {@code rebate_visit_rule_audit}, {@code rebate_visit_rules}.
     *
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     * @throws ResponseStatusException when the method rejects the request with {@code Enter a
     *     non-negative completed-visit minimum and an audit reason.}
     * @throws org.springframework.security.access.AccessDeniedException when the method rejects the
     *     request with {@code Only an administrator can change offers across branches.}
     */
    @PutMapping("/{id}")
    @Transactional
    public void save(@PathVariable long id, @RequestBody Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RebateVisitRuleController.class, "save(long,Input)");
        try {
            staff.requirePermission(PermissionName.REBATE_MANAGE);
            if (input == null
                    || input.minimumCompletedOrders() < 0
                    || input.minimumCompletedOrders() > 100000
                    || input.reason() == null
                    || input.reason().isBlank()
                    || input.reason().length() > 500)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Enter a non-negative completed-visit minimum and an audit reason.");
            var row = jdbc.queryForMap("SELECT branch_id FROM rebates WHERE id=? FOR UPDATE", id);
            if (row.get("branch_id") != null)
                staff.requireBranchAccess(((Number) row.get("branch_id")).longValue());
            else if (!"OWNER_ADMIN".equals(staff.getCurrentStaff().getRole().getName()))
                throw new org.springframework.security.access.AccessDeniedException(
                        "Only an administrator can change offers across branches.");
            long actor = staff.getCurrentStaff().getId();
            jdbc.update(
                    "INSERT INTO"
                        + " rebate_visit_rules(rebate_id,minimum_completed_orders,updated_by,reason)"
                        + " VALUES (?,?,?,?) ON CONFLICT(rebate_id) DO UPDATE SET"
                        + " minimum_completed_orders=EXCLUDED.minimum_completed_orders,updated_by=EXCLUDED.updated_by,reason=EXCLUDED.reason,updated_at=CURRENT_TIMESTAMP",
                    id,
                    input.minimumCompletedOrders(),
                    actor,
                    input.reason().trim());
            jdbc.update(
                    "INSERT INTO"
                        + " rebate_visit_rule_audit(rebate_id,minimum_completed_orders,actor,reason)"
                        + " VALUES (?,?,?,?)",
                    id,
                    input.minimumCompletedOrders(),
                    actor,
                    input.reason().trim());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RebateVisitRuleController.class, "save(long,Input)");
        }
    }
}
