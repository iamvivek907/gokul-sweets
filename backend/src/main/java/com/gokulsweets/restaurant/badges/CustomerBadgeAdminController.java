package com.gokulsweets.restaurant.badges;

import com.gokulsweets.restaurant.loyalty.LoyaltyService;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;

/** Owner-only, audited controls for funded customer badge tiers. */
@RestController
@RequestMapping("/api/admin/loyalty/badges")
@RequiredArgsConstructor
public class CustomerBadgeAdminController {
    private final StaffAuthorizationService staff;
    private final JdbcTemplate jdbc;
    private final LoyaltyService loyalty;
    private final ObjectMapper mapper;

    /** Immutable customer badge data and configured benefit contract. */
    public record Input(
            Long id,
            Integer version,
            String code,
            String name,
            String description,
            int requiredOrders,
            BigDecimal minimumSubtotal,
            BigDecimal bonusPercent,
            String appearance,
            boolean active,
            String reason) {}

    /** Requires the authenticated owner before accessing funded benefits. */
    private long owner() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerBadgeAdminController.class, "owner()");
        try {
            var user = staff.getCurrentStaff();
            if (!"OWNER_ADMIN".equals(user.getRole().getName()))
                throw new AccessDeniedException("Only the owner may configure badge benefits.");
            return user.getId();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerBadgeAdminController.class, "owner()");
        }
    }

    /** Returns owner-visible badge definitions without caching private configuration. */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerBadgeAdminController.class, "list()");
        try {
            owner();
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(
                            jdbc.queryForList(
                                    "SELECT * FROM customer_badges ORDER BY"
                                            + " required_orders,minimum_subtotal,id"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerBadgeAdminController.class, "list()");
        }
    }

    /** Validates and audits an optimistic badge change under the shared loyalty policy lock. */
    @PutMapping
    @Transactional
    public ResponseEntity<Void> save(@RequestBody Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(CustomerBadgeAdminController.class, "save(Input)");
        try {
            long staffId = owner();
            if (input.name() == null
                    || input.name().trim().isEmpty()
                    || input.name().trim().length() > 80
                    || input.code() == null
                    || !input.code().matches("[A-Z][A-Z0-9_]{0,39}")
                    || input.description() == null
                    || input.description().length() > 240
                    || input.requiredOrders() < 1
                    || input.requiredOrders() > 100000
                    || !money(input.minimumSubtotal(), new BigDecimal("1000000"))
                    || !money(input.bonusPercent(), new BigDecimal("100"))
                    || !Set.of("GOLD", "SILVER", "MAROON")
                            .contains(input.appearance() == null ? "" : input.appearance())
                    || input.reason() == null
                    || input.reason().trim().length() < 8
                    || input.reason().length() > 300)
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Check badge fields and provide an audit reason of 8–300 characters.");
            loyalty.lockPolicyForUpdate();
            Long id = input.id();
            if (id == null) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM customer_badges", Integer.class)
                        >= 30)
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "A maximum of 30 badges is supported. Edit an existing badge.");
                if (jdbc.queryForObject(
                                "SELECT COUNT(*) FROM customer_badges WHERE code=?",
                                Integer.class,
                                input.code())
                        > 0)
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Badge code is already in use.");
                id =
                        jdbc.queryForObject(
                                "INSERT INTO"
                                    + " customer_badges(code,name,description,required_orders,minimum_subtotal,bonus_percent,appearance,active)"
                                    + " VALUES(?,?,?,?,?,?,?,?) RETURNING id",
                                Long.class,
                                input.code(),
                                input.name().trim(),
                                input.description().trim(),
                                input.requiredOrders(),
                                input.minimumSubtotal(),
                                input.bonusPercent(),
                                input.appearance(),
                                input.active());
            } else {
                int changed =
                        jdbc.update(
                                "UPDATE customer_badges SET"
                                    + " name=?,description=?,required_orders=?,minimum_subtotal=?,bonus_percent=?,appearance=?,active=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
                                    + " WHERE id=? AND version=? AND code=?",
                                input.name().trim(),
                                input.description().trim(),
                                input.requiredOrders(),
                                input.minimumSubtotal(),
                                input.bonusPercent(),
                                input.appearance(),
                                input.active(),
                                id,
                                input.version(),
                                input.code());
                if (changed != 1)
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Badge changed in another session. Reload before saving.");
            }
            jdbc.update(
                    "INSERT INTO customer_badge_audit(badge_id,staff_id,reason,definition)"
                            + " VALUES(?,?,?,?::jsonb)",
                    id,
                    staffId,
                    input.reason().trim(),
                    mapper.writeValueAsString(input));
            return ResponseEntity.noContent().build();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, CustomerBadgeAdminController.class, "save(Input)");
        }
    }

    /** Checks bounded, non-negative two-decimal benefit and threshold values. */
    private boolean money(BigDecimal value, BigDecimal maximum) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerBadgeAdminController.class, "money(BigDecimal,BigDecimal)");
        try {
            return value != null
                    && value.signum() >= 0
                    && value.compareTo(maximum) <= 0
                    && value.stripTrailingZeros().scale() <= 2;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerBadgeAdminController.class,
                    "money(BigDecimal,BigDecimal)");
        }
    }
}
