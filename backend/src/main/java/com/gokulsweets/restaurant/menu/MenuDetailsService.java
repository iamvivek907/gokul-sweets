package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Owner-authored presentation metadata, independent of prices and orderability. */
@Service
@RequiredArgsConstructor
public class MenuDetailsService {
    private final JdbcTemplate jdbc;
    private final BranchRepository branches;
    private final StaffAuthorizationService authorization;

    /** Bounded branch-product presentation metadata. */
    public record Details(
            long productId,
            String servingInfo,
            String ingredients,
            String allergens,
            String spiceLevel,
            int photoFocusX,
            int photoFocusY,
            boolean chefPick,
            long version) {}

    /** Validated versioned owner edit. */
    public record Update(
            @Size(max = 120) String servingInfo,
            @Size(max = 600) String ingredients,
            @Size(max = 300) String allergens,
            @Pattern(regexp = "MILD|MEDIUM|HOT") String spiceLevel,
            @Min(0) @Max(100) int photoFocusX,
            @Min(0) @Max(100) int photoFocusY,
            boolean chefPick,
            @PositiveOrZero long version) {}

    /** Returns at most 100 requested published branch-product facts. */
    @Transactional(readOnly = true)
    public List<Details> read(long branchId, List<Long> productIds) {
        long started = MethodTiming.start(MenuDetailsService.class, "read(long,List<Long>)");
        try {
            if (branchId <= 0
                    || productIds == null
                    || productIds.isEmpty()
                    || productIds.size() > 100
                    || productIds.stream().anyMatch(id -> id == null || id <= 0))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Choose between 1 and 100 products.");
            if (branches.findByIdAndActiveTrue(branchId).isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
            return query(branchId, new ArrayList<>(new LinkedHashSet<>(productIds)), true);
        } finally {
            MethodTiming.finish(started, MenuDetailsService.class, "read(long,List<Long>)");
        }
    }

    /** Reads editable facts after menu and branch authorization. */
    @Transactional(readOnly = true)
    public Details adminRead(long branchId, long productId) {
        long started = MethodTiming.start(MenuDetailsService.class, "adminRead(long,long)");
        try {
            authorize(branchId);
            requireProduct(branchId, productId, false);
            return query(branchId, List.of(productId), false).stream()
                    .findFirst()
                    .orElse(new Details(productId, null, null, null, null, 50, 50, false, 0));
        } finally {
            MethodTiming.finish(started, MenuDetailsService.class, "adminRead(long,long)");
        }
    }

    /** Serializes edits with a branch-product lock and rejects stale versions. */
    @Transactional
    public Details save(long branchId, long productId, Update value) {
        long started = MethodTiming.start(MenuDetailsService.class, "save(long,long,Update)");
        try {
            authorize(branchId);
            long id = requireProduct(branchId, productId, true);
            var versions =
                    jdbc.queryForList(
                            "SELECT version FROM branch_menu_details WHERE branch_product_id=?",
                            Long.class,
                            id);
            long current = versions.isEmpty() ? 0 : versions.getFirst();
            if (current != value.version())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Dish details changed. Reload before saving.");
            jdbc.update(
                    """
INSERT INTO branch_menu_details(branch_product_id,serving_info,ingredients,allergens,spice_level,photo_focus_x,photo_focus_y,chef_pick,version)
VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (branch_product_id) DO UPDATE SET
serving_info=EXCLUDED.serving_info,ingredients=EXCLUDED.ingredients,allergens=EXCLUDED.allergens,
spice_level=EXCLUDED.spice_level,photo_focus_x=EXCLUDED.photo_focus_x,photo_focus_y=EXCLUDED.photo_focus_y,
chef_pick=EXCLUDED.chef_pick,version=EXCLUDED.version
""",
                    id,
                    value.servingInfo(),
                    value.ingredients(),
                    value.allergens(),
                    value.spiceLevel(),
                    value.photoFocusX(),
                    value.photoFocusY(),
                    value.chefPick(),
                    current + 1);
            return new Details(
                    productId,
                    value.servingInfo(),
                    value.ingredients(),
                    value.allergens(),
                    value.spiceLevel(),
                    value.photoFocusX(),
                    value.photoFocusY(),
                    value.chefPick(),
                    current + 1);
        } finally {
            MethodTiming.finish(started, MenuDetailsService.class, "save(long,long,Update)");
        }
    }

    /** Checks menu management and selected branch access. */
    private void authorize(long branchId) {
        long started = MethodTiming.start(MenuDetailsService.class, "authorize(long)");
        try {
            authorization.requirePermission(PermissionName.MENU_MANAGE);
            authorization.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(started, MenuDetailsService.class, "authorize(long)");
        }
    }

    /** Finds the selected branch product, optionally under a write lock. */
    private long requireProduct(long branchId, long productId, boolean lock) {
        long started =
                MethodTiming.start(MenuDetailsService.class, "requireProduct(long,long,boolean)");
        try {
            var ids =
                    jdbc.queryForList(
                            "SELECT id FROM branch_products WHERE branch_id=? AND product_id=?"
                                    + (lock ? " FOR UPDATE" : ""),
                            Long.class,
                            branchId,
                            productId);
            if (ids.isEmpty())
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Branch product not found.");
            return ids.getFirst();
        } finally {
            MethodTiming.finish(
                    started, MenuDetailsService.class, "requireProduct(long,long,boolean)");
        }
    }

    /** Reads only requested products using bound query parameters. */
    private List<Details> query(long branchId, List<Long> ids, boolean publishedOnly) {
        long started =
                MethodTiming.start(MenuDetailsService.class, "query(long,List<Long>,boolean)");
        try {
            var args = new ArrayList<Object>();
            args.add(branchId);
            args.addAll(ids);
            return jdbc.query(
                    "SELECT"
                        + " bp.product_id,d.serving_info,d.ingredients,d.allergens,d.spice_level,d.photo_focus_x,d.photo_focus_y,d.chef_pick,d.version"
                        + " FROM branch_menu_details d JOIN branch_products bp ON"
                        + " bp.id=d.branch_product_id JOIN products p ON p.id=bp.product_id WHERE"
                        + " bp.branch_id=? "
                            + (publishedOnly ? "AND p.active=true " : "")
                            + "AND bp.product_id IN ("
                            + String.join(",", Collections.nCopies(ids.size(), "?"))
                            + ")",
                    (rs, n) ->
                            new Details(
                                    rs.getLong(1),
                                    rs.getString(2),
                                    rs.getString(3),
                                    rs.getString(4),
                                    rs.getString(5),
                                    rs.getInt(6),
                                    rs.getInt(7),
                                    rs.getBoolean(8),
                                    rs.getLong(9)),
                    args.toArray());
        } finally {
            MethodTiming.finish(
                    started, MenuDetailsService.class, "query(long,List<Long>,boolean)");
        }
    }
}
