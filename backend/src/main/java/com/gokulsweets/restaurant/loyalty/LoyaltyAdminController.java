package com.gokulsweets.restaurant.loyalty;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import tools.jackson.databind.ObjectMapper;

import java.math.*;
import java.util.*;

/** HTTP endpoints for loyalty admin operations. */
@RestController
@RequestMapping("/api/admin/loyalty")
@RequiredArgsConstructor
public class LoyaltyAdminController {

    private final StaffAuthorizationService staff;

    private final JdbcTemplate jdbc;

    private final LoyaltyService loyalty;

    private final LoyaltyProperties rules;

    private final ObjectMapper mapper;

    /**
     * Owners the operation.
     *
     * @return the owner result
     */
    private long owner() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyAdminController.class, "owner()");
        try {
            var user = staff.getCurrentStaff();
            if (!"OWNER_ADMIN".equals(user.getRole().getName()))
                throw new AccessDeniedException(
                        "Only the owner may change funded loyalty benefits.");
            return user.getId();
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, LoyaltyAdminController.class, "owner()");
        }
    }

    /**
     * Immutable reward input data contract.
     *
     * @param code the code
     * @param name the name
     * @param coins the coins
     * @param discount the discount
     * @param minimumSubtotal the minimum subtotal
     * @param active the active
     * @param reason the reason
     */
    public record RewardInput(
            String code,
            String name,
            int coins,
            BigDecimal discount,
            BigDecimal minimumSubtotal,
            boolean active,
            String reason) {}

    /**
     * Immutable exclusions data contract.
     *
     * @param productIds the product ids
     * @param rebateCodes the rebate codes
     * @param reason the reason
     */
    public record Exclusions(List<Long> productIds, List<String> rebateCodes, String reason) {}

    /**
     * Reports the operation.
     *
     * @return the report result
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> report() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyAdminController.class, "report()");
        try {
            owner();
            var output = new LinkedHashMap<String, Object>();
            output.put("enabled", loyalty.enabled());
            output.put("rules", rules);
            output.put(
                    "rewards", jdbc.queryForList("SELECT * FROM loyalty_rewards ORDER BY coins"));
            output.put(
                    "accounts",
                    jdbc.queryForList(
                            "SELECT a.environment,a.subject_id,COALESCE(SUM(l.coins),0) AS coins"
                                + " FROM loyalty_accounts a LEFT JOIN loyalty_ledger l ON"
                                + " l.environment=a.environment AND l.subject_id=a.subject_id GROUP"
                                + " BY a.environment,a.subject_id ORDER BY"
                                + " a.environment,a.subject_id LIMIT 500"));
            output.put(
                    "adjustments",
                    jdbc.queryForList(
                            "SELECT a.*,l.environment,l.subject_id,l.coins,l.created_at FROM"
                                    + " loyalty_adjustment_audit a JOIN loyalty_ledger l ON"
                                    + " l.id=a.ledger_id ORDER BY l.id DESC LIMIT 100"));
            output.put(
                    "exclusions",
                    Map.of(
                            "productIds",
                            jdbc.queryForList(
                                    "SELECT product_id FROM loyalty_excluded_products", Long.class),
                            "rebateCodes",
                            jdbc.queryForList(
                                    "SELECT code FROM loyalty_excluded_rebates", String.class)));
            BigDecimal face =
                    jdbc.queryForObject(
                            "SELECT COALESCE(MAX(discount/coins),0) FROM loyalty_rewards",
                            BigDecimal.class);
            long outstanding =
                    jdbc.queryForObject(
                            "SELECT COALESCE(SUM(GREATEST(coins,0)),0) FROM (SELECT SUM(coins)"
                                    + " coins FROM loyalty_ledger GROUP BY environment,subject_id)"
                                    + " balances",
                            Long.class);
            output.put(
                    "reservedDiscountRupees",
                    jdbc.queryForObject(
                            "SELECT COALESCE(SUM(discount),0) FROM loyalty_holds WHERE"
                                    + " state='RESERVED'",
                            BigDecimal.class));
            output.put("availableCoinLiabilityIncludesPausedRewards", true);
            output.put(
                    "liability",
                    List.of(25, 50, 80, 100).stream()
                            .map(
                                    percent ->
                                            Map.of(
                                                    "redemptionPercent",
                                                    percent,
                                                    "rupees",
                                                    face.multiply(BigDecimal.valueOf(outstanding))
                                                            .multiply(BigDecimal.valueOf(percent))
                                                            .divide(
                                                                    new BigDecimal("100"),
                                                                    2,
                                                                    RoundingMode.HALF_UP)))
                            .toList());
            output.put(
                    "normalMaximumCostPercent",
                    face.multiply(new BigDecimal("100"))
                            .divide(rules.getRupeesPerCoin(), 2, RoundingMode.HALF_UP));
            output.put(
                    "orderMetrics",
                    jdbc.queryForList(
                            "SELECT own.environment,o.branch_id,COUNT(*) AS"
                                + " enrolled_orders,COUNT(*) FILTER(WHERE o.order_status IN"
                                + " ('PICKED_UP','DELIVERED')) AS"
                                + " completed_orders,COALESCE(SUM(o.loyalty_discount),0) AS"
                                + " reward_savings,COALESCE(SUM(o.subtotal),0) AS product_subtotal"
                                + " FROM orders o JOIN verified_order_ownership own ON"
                                + " own.order_id=o.id WHERE o.loyalty_enrolled GROUP BY"
                                + " own.environment,o.branch_id"));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(output);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyAdminController.class, "report()");
        }
    }

    /**
     * Rewards the operation.
     *
     * @param input the input
     */
    @PutMapping("/rewards")
    @Transactional
    public void reward(@RequestBody RewardInput input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyAdminController.class, "reward(RewardInput)");
        try {
            long actor = owner();
            reason(input.reason());
            if (input.code() == null
                    || !input.code().matches("[A-Z0-9_]{1,40}")
                    || input.name() == null
                    || input.name().isBlank()
                    || input.name().length() > 100
                    || input.coins() < 1
                    || input.coins() > 100000
                    || input.discount() == null
                    || input.discount().signum() <= 0
                    || input.minimumSubtotal() == null
                    || input.minimumSubtotal().signum() <= 0
                    || input.discount()
                                    .multiply(new BigDecimal("100"))
                                    .compareTo(
                                            BigDecimal.valueOf(input.coins())
                                                    .multiply(rules.getRupeesPerCoin())
                                                    .multiply(rules.getNormalMaximumCostPercent()))
                            > 0)
                throw new IllegalArgumentException(
                        "A funded reward needs valid amounts and must stay within the normal 3%"
                                + " coin-cost budget.");
            loyalty.lockPolicyForUpdate();
            var before =
                    jdbc.queryForList(
                            "SELECT * FROM loyalty_rewards WHERE code=? FOR UPDATE", input.code());
            jdbc.update(
                    "INSERT INTO loyalty_rewards(code,name,coins,discount,minimum_subtotal,active)"
                        + " VALUES (?,?,?,?,?,?) ON CONFLICT(code) DO UPDATE SET"
                        + " name=EXCLUDED.name,coins=EXCLUDED.coins,discount=EXCLUDED.discount,minimum_subtotal=EXCLUDED.minimum_subtotal,active=EXCLUDED.active,version=loyalty_rewards.version+1",
                    input.code(),
                    input.name().trim(),
                    input.coins(),
                    input.discount(),
                    input.minimumSubtotal(),
                    input.active());
            audit(actor, "REWARD", input.code(), input.reason(), before, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyAdminController.class, "reward(RewardInput)");
        }
    }

    /**
     * Exclusionses the operation.
     *
     * @param input the input
     */
    @PutMapping("/exclusions")
    @Transactional
    public void exclusions(@RequestBody Exclusions input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyAdminController.class, "exclusions(Exclusions)");
        try {
            long actor = owner();
            reason(input.reason());
            if (input.productIds() == null
                    || input.rebateCodes() == null
                    || input.productIds().size() > 500
                    || input.rebateCodes().size() > 500
                    || input.productIds().stream().anyMatch(id -> id == null || id <= 0)
                    || input.rebateCodes().stream()
                            .anyMatch(code -> code == null || !code.matches("[A-Z0-9_-]{1,100}")))
                throw new IllegalArgumentException("Invalid excluded products or promotion codes.");
            loyalty.lockPolicyForUpdate();
            var before =
                    Map.of(
                            "products",
                            jdbc.queryForList("SELECT * FROM loyalty_excluded_products"),
                            "rebates",
                            jdbc.queryForList("SELECT * FROM loyalty_excluded_rebates"));
            jdbc.update("DELETE FROM loyalty_excluded_products");
            jdbc.update("DELETE FROM loyalty_excluded_rebates");
            for (long id : new HashSet<>(input.productIds()))
                jdbc.update("INSERT INTO loyalty_excluded_products VALUES (?)", id);
            for (String code : new HashSet<>(input.rebateCodes()))
                jdbc.update("INSERT INTO loyalty_excluded_rebates VALUES (?)", code);
            audit(actor, "EXCLUSIONS", "program", input.reason(), before, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyAdminController.class,
                    "exclusions(Exclusions)");
        }
    }

    /**
     * Adjusts the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @param input the input
     * @return the adjust result
     */
    @PostMapping("/accounts/{environment}/{subject}/adjustments")
    public LoyaltyService.Adjusted adjust(
            @PathVariable String environment,
            @PathVariable UUID subject,
            @RequestBody LoyaltyService.Adjustment input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        LoyaltyAdminController.class,
                        "adjust(String,UUID,LoyaltyService.Adjustment)");
        try {
            return loyalty.adjust(environment, subject, input, owner());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyAdminController.class,
                    "adjust(String,UUID,LoyaltyService.Adjustment)");
        }
    }

    /**
     * Ledgers the operation.
     *
     * @param environment the environment
     * @param subject the subject
     * @return the ledger result
     */
    @GetMapping("/accounts/{environment}/{subject}/ledger")
    public ResponseEntity<List<Map<String, Object>>> ledger(
            @PathVariable String environment, @PathVariable UUID subject) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyAdminController.class, "ledger(String,UUID)");
        try {
            owner();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            jdbc.queryForList(
                                    "SELECT * FROM loyalty_ledger WHERE environment=? AND"
                                            + " subject_id=? ORDER BY id DESC LIMIT 500",
                                    environment,
                                    subject));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyAdminController.class, "ledger(String,UUID)");
        }
    }

    /**
     * Reasons the operation.
     *
     * @param reason the reason
     */
    private void reason(String reason) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(LoyaltyAdminController.class, "reason(String)");
        try {
            if (reason == null || reason.trim().length() < 8 || reason.length() > 300)
                throw new IllegalArgumentException(
                        "A clear audit reason (8–300 characters) is required.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, LoyaltyAdminController.class, "reason(String)");
        }
    }

    /**
     * Audits the operation.
     *
     * @param actor the actor
     * @param kind the kind
     * @param target the target
     * @param reason the reason
     * @param before the before
     * @param after the after
     */
    private void audit(
            long actor, String kind, String target, String reason, Object before, Object after) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        LoyaltyAdminController.class,
                        "audit(long,String,String,String,Object,Object)");
        try {
            try {
                jdbc.update(
                        "INSERT INTO"
                            + " loyalty_admin_audit(staff_id,kind,target,reason,before_value,after_value)"
                            + " VALUES (?,?,?,?,?::jsonb,?::jsonb)",
                        actor,
                        kind,
                        target,
                        reason,
                        mapper.writeValueAsString(before),
                        mapper.writeValueAsString(after));
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Loyalty audit could not be written", failure);
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    LoyaltyAdminController.class,
                    "audit(long,String,String,String,Object,Object)");
        }
    }
}
