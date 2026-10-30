package com.gokulsweets.restaurant.meal;

import static com.gokulsweets.restaurant.meal.MealModels.*;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.math.*;
import java.util.*;

/** Bounded branch meal reads, optimistic owner edits and trusted component pricing. */
@Service
@RequiredArgsConstructor
public class MealService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final StaffAuthorizationService authorization;

    /** Reads at most 32 meals; public responses never expose owner costs. */
    public List<Definition> catalog(long branchId, boolean admin) {
        long started = MethodTiming.start(MealService.class, "catalog(long,boolean)");
        try {
            if (admin) authorize(branchId);
            else if (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM branches WHERE id=? AND active=true)",
                            Boolean.class,
                            branchId))) return List.of();
            return jdbc
                    .query(
                            "SELECT * FROM branch_meals WHERE branch_id=? "
                                    + (admin ? "" : "AND active=true ")
                                    + "ORDER BY id LIMIT 33",
                            (rs, n) ->
                                    new Definition(
                                            rs.getLong("id"),
                                            branchId,
                                            rs.getLong("version"),
                                            rs.getString("title"),
                                            rs.getString("title_hi"),
                                            rs.getString("description"),
                                            rs.getString("image_url"),
                                            rs.getBigDecimal("base_price"),
                                            rs.getString("discount_type"),
                                            rs.getBigDecimal("discount_value"),
                                            admin ? rs.getBigDecimal("estimated_cost") : null,
                                            rs.getBoolean("active"),
                                            List.of(
                                                    mapper.readValue(
                                                            rs.getString("groups"), Group[].class)),
                                            rs.getString("pricing_mode"),
                                            rs.getBigDecimal("maximum_discount")),
                            branchId)
                    .stream()
                    .limit(32)
                    .toList();
        } finally {
            MethodTiming.finish(started, MealService.class, "catalog(long,boolean)");
        }
    }

    /** Resolves only a published meal belonging to the selected branch. */
    public Definition definition(long branchId, long id) {
        long started = MethodTiming.start(MealService.class, "definition(long,long)");
        try {
            return catalog(branchId, false).stream()
                    .filter(m -> m.id() == id)
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalArgumentException(
                                            "This meal is no longer available. Choose another"
                                                    + " meal."));
        } finally {
            MethodTiming.finish(started, MealService.class, "definition(long,long)");
        }
    }

    /** Loads the current branch SKU facts in one bounded query without historical scans. */
    public Map<Long, Product> products(long branchId, List<Long> ids) {
        long started = MethodTiming.start(MealService.class, "products(long,List<Long>)");
        try {
            if (ids == null || ids.isEmpty() || ids.size() > 160)
                throw new IllegalArgumentException("Configure at most 160 meal dish options.");
            List<Object> parameters = new ArrayList<>();
            parameters.add(branchId);
            parameters.addAll(ids);
            var rows =
                    jdbc.query(
                            "SELECT p.id,p.name,p.sale_mode,COALESCE(b.price_override,p.base_price)"
                                + " price,COALESCE(p.minimum_weight_grams,250)"
                                + " minimum_grams,COALESCE(p.weight_step_grams,50)"
                                + " step_grams,(p.active AND b.available) available FROM"
                                + " branch_products b JOIN products p ON p.id=b.product_id WHERE"
                                + " b.branch_id=? AND p.id IN ("
                                    + String.join(",", Collections.nCopies(ids.size(), "?"))
                                    + ") ORDER BY p.id",
                            (rs, n) ->
                                    new Product(
                                            rs.getLong("id"),
                                            rs.getString("name"),
                                            rs.getString("sale_mode"),
                                            rs.getBigDecimal("price"),
                                            rs.getInt("minimum_grams"),
                                            rs.getInt("step_grams"),
                                            rs.getBoolean("available")),
                            parameters.toArray());
            Map<Long, Product> result = new HashMap<>();
            for (Product p : rows) result.put(p.id(), p);
            return result;
        } finally {
            MethodTiming.finish(started, MealService.class, "products(long,List<Long>)");
        }
    }

    /** Returns a trusted meal quote; checkout independently repeats all validation. */
    public Quote quote(long branchId, Claim claim) {
        long started = MethodTiming.start(MealService.class, "quote(long,Claim)");
        try {
            if (claim == null) throw new IllegalArgumentException("Choose a meal first.");
            Definition definition = definition(branchId, claim.mealId());
            return MealPolicy.quote(definition, claim, products(branchId, optionIds(definition)));
        } finally {
            MethodTiming.finish(started, MealService.class, "quote(long,Claim)");
        }
    }

    /** Saves validated prices and choices atomically, rejecting stale owner edits. */
    @Transactional
    public Definition save(long branchId, long id, Edit edit) {
        long started = MethodTiming.start(MealService.class, "save(long,long,Edit)");
        try {
            authorize(branchId);
            if (edit == null || edit.version() < 0)
                throw new IllegalArgumentException("Check the meal edit version.");
            Definition value =
                    new Definition(
                            id,
                            branchId,
                            edit.version(),
                            edit.title(),
                            edit.titleHi(),
                            edit.description(),
                            edit.imageUrl(),
                            edit.basePrice(),
                            edit.discountType(),
                            edit.discountValue(),
                            edit.estimatedCost(),
                            edit.active(),
                            edit.groups(),
                            edit.pricingMode(),
                            edit.maximumDiscount());
            MealPolicy.validate(value, products(branchId, optionIds(value)));
            String groups = mapper.writeValueAsString(edit.groups());
            // Serialize the 32-meal branch bound even when two admins create concurrently.
            var branches =
                    jdbc.queryForList(
                            "SELECT id FROM branches WHERE id=? FOR UPDATE", Long.class, branchId);
            if (branches.isEmpty()) throw new IllegalArgumentException("Branch not found.");
            if (id == 0) {
                if (jdbc.queryForObject(
                                "SELECT COUNT(*) FROM branch_meals WHERE branch_id=?",
                                Long.class,
                                branchId)
                        >= 32)
                    throw new IllegalArgumentException(
                            "Edit an existing meal; each branch supports 32 definitions.");
                id =
                        jdbc.queryForObject(
                                "INSERT INTO"
                                    + " branch_meals(branch_id,title,title_hi,description,image_url,base_price,discount_type,discount_value,estimated_cost,active,groups,pricing_mode,maximum_discount)"
                                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?) RETURNING id",
                                Long.class,
                                branchId,
                                edit.title().trim(),
                                edit.titleHi(),
                                edit.description(),
                                edit.imageUrl(),
                                edit.basePrice(),
                                edit.discountType(),
                                edit.discountValue(),
                                edit.estimatedCost(),
                                edit.active(),
                                groups,
                                edit.pricingMode(),
                                edit.maximumDiscount());
            } else if (jdbc.update(
                            "UPDATE branch_meals SET"
                                + " title=?,title_hi=?,description=?,image_url=?,base_price=?,discount_type=?,discount_value=?,estimated_cost=?,active=?,groups=?::jsonb,pricing_mode=?,maximum_discount=?,version=version+1,updated_at=CURRENT_TIMESTAMP"
                                + " WHERE branch_id=? AND id=? AND version=?",
                            edit.title().trim(),
                            edit.titleHi(),
                            edit.description(),
                            edit.imageUrl(),
                            edit.basePrice(),
                            edit.discountType(),
                            edit.discountValue(),
                            edit.estimatedCost(),
                            edit.active(),
                            groups,
                            edit.pricingMode(),
                            edit.maximumDiscount(),
                            branchId,
                            id,
                            edit.version())
                    != 1)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This meal was edited elsewhere. Reload before saving.");
            final long savedId = id;
            return catalog(branchId, true).stream()
                    .filter(m -> m.id() == savedId)
                    .findFirst()
                    .orElseThrow();
        } finally {
            MethodTiming.finish(started, MealService.class, "save(long,long,Edit)");
        }
    }

    /** Requires both menu-management permission and selected branch access. */
    private void authorize(long branchId) {
        long started = MethodTiming.start(MealService.class, "authorize(long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            authorization.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(started, MealService.class, "authorize(long)");
        }
    }

    /** Collects bounded SKU references before any catalog query. */
    private List<Long> optionIds(Definition meal) {
        long started = MethodTiming.start(MealService.class, "optionIds(Definition)");
        try {
            if (meal.groups() == null || meal.groups().isEmpty() || meal.groups().size() > 8)
                throw new IllegalArgumentException("Configure 1 to 8 meal groups.");
            var ids =
                    meal.groups().stream()
                            .filter(Objects::nonNull)
                            .flatMap(
                                    g ->
                                            g.options() == null
                                                    ? java.util.stream.Stream.empty()
                                                    : g.options().stream())
                            .filter(Objects::nonNull)
                            .map(Option::productId)
                            .distinct()
                            .sorted()
                            .toList();
            if (ids.isEmpty() || ids.size() > 160)
                throw new IllegalArgumentException("Configure between 1 and 160 dish options.");
            return ids;
        } finally {
            MethodTiming.finish(started, MealService.class, "optionIds(Definition)");
        }
    }
}
