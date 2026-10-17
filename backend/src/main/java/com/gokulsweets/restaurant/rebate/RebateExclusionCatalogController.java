package com.gokulsweets.restaurant.rebate;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded, named choices for offer managers; no stock or customer data is returned. */
@RestController
@RequestMapping("/api/admin/rebates/exclusion-options")
@RequiredArgsConstructor
public class RebateExclusionCatalogController {
    private final StaffAuthorizationService authorization;
    private final NamedParameterJdbcTemplate jdbc;

    /** Named catalogue option. */
    public record Choice(long id, String name, Long categoryId) {}

    /** One bounded result page and the retained selections. */
    public record Catalog(
            List<Choice> categories,
            List<Choice> products,
            List<Choice> selectedProducts,
            boolean hasMore) {}

    /** Returns authorized, paginated choices for a branch or an owner’s global offer. */
    @GetMapping
    public Catalog options(
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Set<Long> selected) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateExclusionCatalogController.class,
                        "options(Long,String,int,Set<Long>)");
        try {
            authorization.requirePermission(PermissionName.REBATE_MANAGE);
            if (branchId == null) {
                if (!"OWNER_ADMIN".equals(authorization.getCurrentStaff().getRole().getName()))
                    throw new AccessDeniedException("Choose an accessible branch.");
            } else {
                if (branchId <= 0) throw new IllegalArgumentException("Choose a valid branch.");
                authorization.requireBranchAccess(branchId);
            }
            if (page < 0
                    || page > 10000
                    || search.length() > 100
                    || selected != null
                            && (selected.size() > 500
                                    || selected.stream().anyMatch(id -> id == null || id <= 0)))
                throw new IllegalArgumentException("Invalid exclusion search.");
            var params = new java.util.HashMap<String, Object>();
            params.put("search", search.trim().toLowerCase(java.util.Locale.ROOT));
            params.put("offset", page * 50);
            String scope =
                    branchId == null
                            ? ""
                            : " AND EXISTS (SELECT 1 FROM branch_products bp WHERE"
                                  + " bp.product_id=p.id AND bp.branch_id=:branch)";
            if (branchId != null) params.put("branch", branchId);
            var products =
                    jdbc.query(
                            "SELECT p.id,p.name,p.category_id FROM products p WHERE"
                                + " position(:search in lower(p.name || ' ' || p.code)) > 0"
                                    + scope
                                    + " ORDER BY lower(p.name),p.id LIMIT 51 OFFSET :offset",
                            params,
                            (rs, n) ->
                                    new Choice(
                                            rs.getLong("id"),
                                            rs.getString("name"),
                                            rs.getLong("category_id")));
            var selectedProducts =
                    selected == null || selected.isEmpty()
                            ? List.<Choice>of()
                            : jdbc.query(
                                    "SELECT p.id,p.name,p.category_id FROM products p WHERE p.id IN"
                                        + " (:selected)"
                                            + scope
                                            + " ORDER BY lower(p.name),p.id",
                                    withSelected(params, selected),
                                    (rs, n) ->
                                            new Choice(
                                                    rs.getLong("id"),
                                                    rs.getString("name"),
                                                    rs.getLong("category_id")));
            var categories =
                    jdbc.query(
                            "SELECT id,name FROM categories ORDER BY display_order,id",
                            Map.of(),
                            (rs, n) -> new Choice(rs.getLong("id"), rs.getString("name"), null));
            return new Catalog(
                    categories,
                    products.stream().limit(50).toList(),
                    selectedProducts,
                    products.size() > 50);

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateExclusionCatalogController.class,
                    "options(Long,String,int,Set<Long>)");
        }
    }

    /** Computes withSelected for offer exclusions. */
    private Map<String, Object> withSelected(Map<String, Object> params, Set<Long> selected) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RebateExclusionCatalogController.class,
                        "withSelected(Map<String,Object>,Set<Long>)");
        try {
            var copy = new java.util.HashMap<>(params);
            copy.put("selected", selected);
            return copy;

        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RebateExclusionCatalogController.class,
                    "withSelected(Map<String,Object>,Set<Long>)");
        }
    }
}
