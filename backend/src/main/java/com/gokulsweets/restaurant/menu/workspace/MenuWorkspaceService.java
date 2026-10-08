package com.gokulsweets.restaurant.menu.workspace;

import com.gokulsweets.restaurant.inventory.dto.*;
import com.gokulsweets.restaurant.inventory.repository.*;
import com.gokulsweets.restaurant.inventory.service.InventoryAvailabilityService;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.product.*;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Coordinates menu workspace operations. */
@Service
@RequiredArgsConstructor
public class MenuWorkspaceService {

    private final JdbcTemplate jdbc;

    private final StaffAuthorizationService staff;

    private final ProductService products;

    private final com.gokulsweets.restaurant.inventory.service.AdminInventoryService inventory;

    private final BranchInventoryPolicyRepository policies;

    private final InventoryDailyAllocationRepository allocations;

    private final InventoryAvailabilityService availability;

    /** Immutable details data contract. */
    public record Details(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 100) String code,
            @NotNull @Positive Long categoryId,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("0.01") BigDecimal basePrice,
            @NotNull ProductSaleMode saleMode,
            @Min(1) Integer minimumWeightGrams,
            @Min(1) Integer weightStepGrams,
            Long taxCategoryId,
            @NotNull @Min(0) Long version) {}

    /** Immutable branch edit data contract. */
    public record BranchEdit(
            @NotNull @Min(0) Long version,
            Boolean available,
            @DecimalMin("0.01") BigDecimal priceOverride,
            Boolean clearPriceOverride) {}

    /** Immutable option data contract. */
    public record Option(Long id, String name) {}

    /** Immutable page data contract. */
    public record Page(
            List<Map<String, Object>> content,
            long totalElements,
            int page,
            int totalPages,
            List<Option> categories,
            List<Option> taxes,
            List<Option> branchCategories) {}

    /**
     * Authorizes the operation.
     *
     * @param branch the branch
     * @param inventory the inventory
     */
    private void authorize(long branch, boolean inventory) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceService.class, "authorize(long,boolean)");
        try {
            staff.requirePermission(PermissionName.MENU_MANAGE);
            staff.requireBranchAccess(branch);
            if (inventory) staff.requirePermission(PermissionName.INVENTORY_VIEW);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceService.class,
                    "authorize(long,boolean)");
        }
    }

    /**
     * Shareds the operation.
     *
     * @param branch the branch
     * @param product the product
     */
    private void shared(long branch, long product) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceService.class, "shared(long,long)");
        try {
            authorize(branch, false);
            var bs =
                    jdbc.queryForList(
                            "SELECT branch_id FROM branch_products WHERE product_id=? ORDER BY"
                                    + " branch_id",
                            Long.class,
                            product);
            if (!bs.contains(branch))
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not assigned to this branch.");
            bs.forEach(staff::requireBranchAccess);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuWorkspaceService.class, "shared(long,long)");
        }
    }

    private static final String BALANCE = AppConstant.MENU_WORKSPACE_SERVICE_BALANCE;

    /**
     * Lists the operation.
     *
     * @param branch the branch
     * @param date the date
     * @param search the search
     * @param category the category
     * @param filter the filter
     * @param page the page
     * @param size the size
     * @return the list result
     */
    @Transactional(readOnly = true)
    public Page list(
            long branch,
            LocalDate date,
            String search,
            Long category,
            String filter,
            int page,
            int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceService.class,
                        "list(long,LocalDate,String,Long,String,int,int)");
        try {
            authorize(branch, true);
            if (page < 0 || page > 100000 || size < 1 || size > 50)
                throw new IllegalArgumentException("Use a page size between 1 and 50.");
            var args = new HashMap<String, Object>();
            args.put("branch", branch);
            args.put("date", date);
            args.put("limit", size);
            args.put("offset", (long) page * size);
            String from =
                    " FROM branch_products bp JOIN products p ON p.id=bp.product_id JOIN categories"
                        + " c ON c.id=p.category_id LEFT JOIN branch_inventory_policies pol ON"
                        + " pol.branch_product_id=bp.id LEFT JOIN inventory_daily_allocations al ON"
                        + " al.branch_product_id=bp.id AND al.service_date=:date WHERE"
                        + " bp.branch_id=:branch";
            if (search != null && !search.isBlank()) {
                if (search.length() > 100)
                    throw new IllegalArgumentException("Search is too long.");
                args.put(
                        "search",
                        "%"
                                + search.trim()
                                        .toLowerCase(Locale.ROOT)
                                        .replace("\\", "\\\\")
                                        .replace("%", "\\%")
                                        .replace("_", "\\_")
                                + "%");
                from += " AND (lower(p.name) LIKE :search OR lower(p.code) LIKE :search)";
            }
            if (category != null) {
                args.put("category", category);
                from += " AND p.category_id=:category";
            }
            from +=
                    switch (filter) {
                        case "ALL" -> "";
                        case "COUNT_SKU" -> " AND p.sale_mode='UNIT'";
                        case "UNAVAILABLE" ->
                                " AND (NOT bp.available OR NOT p.active OR NOT c.active)";
                        case "MISSING_IMAGE" -> " AND (p.image_url IS NULL OR p.image_url='')";
                        case "OVERRIDE" -> " AND bp.price_override IS NOT NULL";
                        case "GROUPED" ->
                                " AND EXISTS(SELECT 1 FROM mobile_menu_choices mc WHERE"
                                        + " mc.branch_id=bp.branch_id AND mc.product_id=p.id)";
                        case "LOW_STOCK" ->
                                " AND al.id IS NOT NULL AND "
                                        + BALANCE
                                        + "<=al.safety_buffer_quantity";
                        default -> throw new IllegalArgumentException("Unknown filter.");
                    };
            var named = new NamedParameterJdbcTemplate(jdbc);
            long total =
                    Objects.requireNonNull(
                            named.queryForObject("SELECT count(*)" + from, args, Long.class));
            var rows =
                    named.queryForList(
                            "SELECT bp.id AS \"branchProductId\",p.id AS"
                                + " \"productId\",p.code,p.name,p.description,p.category_id AS"
                                + " \"categoryId\",c.name AS \"categoryName\",p.base_price AS"
                                + " \"basePrice\",bp.price_override AS"
                                + " \"priceOverride\",COALESCE(bp.price_override,p.base_price) AS"
                                + " \"effectivePrice\",bp.available,p.active,p.vegetarian,p.sale_mode"
                                + " AS \"saleMode\",p.minimum_weight_grams AS"
                                + " \"minimumWeightGrams\",p.weight_step_grams AS"
                                + " \"weightStepGrams\",p.tax_category_id AS"
                                + " \"taxCategoryId\",p.image_url AS"
                                + " \"imageUrl\",p.workspace_version AS"
                                + " \"productVersion\",bp.workspace_version AS \"branchVersion\""
                                    + from
                                    + " ORDER BY c.display_order,bp.display_order,p.name,p.id LIMIT"
                                    + " :limit OFFSET :offset",
                            args);
            if (!rows.isEmpty()) {
                var ids =
                        rows.stream()
                                .map(r -> ((Number) r.get("branchProductId")).longValue())
                                .toList();
                var ps =
                        new HashMap<
                                Long,
                                com.gokulsweets.restaurant.inventory.entity
                                        .BranchInventoryPolicy>();
                policies.findByBranchProductIdIn(ids)
                        .forEach(p -> ps.put(p.getBranchProduct().getId(), p));
                var als =
                        new HashMap<
                                Long,
                                com.gokulsweets.restaurant.inventory.entity
                                        .InventoryDailyAllocation>();
                allocations
                        .findByBranchProductIdInAndServiceDate(ids, date)
                        .forEach(a -> als.put(a.getBranchProduct().getId(), a));
                for (var row : rows) {
                    long id = ((Number) row.get("branchProductId")).longValue();
                    var p = ps.get(id);
                    var a = als.get(id);
                    row.put("policyVersion", p == null ? null : p.getVersion());
                    row.put("allocationVersion", a == null ? null : a.getVersion());
                    row.put("policy", p == null ? null : InventoryPolicyResponse.from(p));
                    row.put(
                            "allocation",
                            p == null || a == null
                                    ? null
                                    : InventoryAllocationResponse.from(
                                            a, availability.calculate(a, p)));
                }
            }
            var cats =
                    jdbc.query(
                            "SELECT id,name FROM categories WHERE active=true ORDER BY name LIMIT"
                                    + " 200",
                            (rs, n) -> new Option(rs.getLong(1), rs.getString(2)));
            var taxes =
                    jdbc.query(
                            "SELECT id,name FROM tax_categories WHERE active=true ORDER BY name"
                                    + " LIMIT 200",
                            (rs, n) -> new Option(rs.getLong(1), rs.getString(2)));
            var branchCats =
                    jdbc.query(
                            "SELECT c.id,c.name FROM categories c WHERE c.active=true AND"
                                + " EXISTS(SELECT 1 FROM products p JOIN branch_products bp ON"
                                + " bp.product_id=p.id WHERE p.category_id=c.id AND bp.branch_id=?)"
                                + " ORDER BY c.name LIMIT 200",
                            (rs, n) -> new Option(rs.getLong(1), rs.getString(2)),
                            branch);
            return new Page(
                    rows, total, page, (int) ((total + size - 1) / size), cats, taxes, branchCats);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceService.class,
                    "list(long,LocalDate,String,Long,String,int,int)");
        }
    }

    /**
     * Products the operation.
     *
     * @param id the id
     * @param lock the lock
     * @return the product result
     */
    private Map<String, Object> product(long id, boolean lock) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceService.class, "product(long,boolean)");
        try {
            var rows =
                    jdbc.queryForList(
                            "SELECT * FROM products WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
            if (rows.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
            return rows.getFirst();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuWorkspaceService.class, "product(long,boolean)");
        }
    }

    /**
     * Audits the operation.
     *
     * @param branch the branch
     * @param product the product
     * @param action the action
     * @param before the before
     * @param after the after
     */
    private void audit(long branch, Long product, String action, Object before, Object after) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceService.class, "audit(long,Long,String,Object,Object)");
        try {
            jdbc.update(
                    "INSERT INTO"
                        + " menu_workspace_audit(actor,branch_id,product_id,action,before_state,after_state)"
                        + " VALUES (?,?,?,?,?,?)",
                    staff.getCurrentStaff().getUsername(),
                    branch,
                    product,
                    action,
                    String.valueOf(before),
                    String.valueOf(after));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceService.class,
                    "audit(long,Long,String,Object,Object)");
        }
    }

    /**
     * Validates the operation.
     *
     * @param d the d
     */
    private void validate(Details d) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceService.class, "validate(Details)");
        try {
            if (d.saleMode() == ProductSaleMode.WEIGHT
                    && (d.minimumWeightGrams() == null || d.weightStepGrams() == null))
                throw new IllegalArgumentException(
                        "Weight products need minimum and step quantities.");
            if (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM categories WHERE id=? AND active=true)",
                            Boolean.class,
                            d.categoryId())))
                throw new IllegalArgumentException("Choose an active category.");
            if (d.taxCategoryId() != null
                    && !Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM tax_categories WHERE id=? AND"
                                            + " active=true)",
                                    Boolean.class,
                                    d.taxCategoryId())))
                throw new IllegalArgumentException("Choose an active tax category.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuWorkspaceService.class, "validate(Details)");
        }
    }

    /**
     * Edits details.
     *
     * @param branch the branch
     * @param id the id
     * @param d the d
     */
    @Transactional
    public void editDetails(long branch, long id, Details d) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceService.class, "editDetails(long,long,Details)");
        try {
            shared(branch, id);
            validate(d);
            var old = product(id, true);
            if (((Number) old.get("workspace_version")).longValue() != d.version()) conflict();
            // Sale-unit changes invalidate existing stock units and orders; use a new SKU instead.
            if (!Objects.equals(((Number) old.get("category_id")).longValue(), d.categoryId())
                    && jdbc.queryForObject(
                                    "SELECT count(*) FROM mobile_menu_choices WHERE product_id=?",
                                    Long.class,
                                    id)
                            > 0)
                throw new IllegalArgumentException(
                        "Ungroup this product before changing its category.");
            if (!old.get("sale_mode").equals(d.saleMode().name()))
                throw new IllegalArgumentException(
                        "An existing SKU's sale unit cannot change. Create a new product instead.");
            jdbc.update(
                    "UPDATE products SET"
                        + " name=?,code=?,category_id=?,description=?,base_price=?,minimum_weight_grams=?,weight_step_grams=?,tax_category_id=?,updated_at=now()"
                        + " WHERE id=?",
                    d.name().trim(),
                    d.code().trim(),
                    d.categoryId(),
                    d.description(),
                    d.basePrice(),
                    d.saleMode() == ProductSaleMode.WEIGHT ? d.minimumWeightGrams() : null,
                    d.saleMode() == ProductSaleMode.WEIGHT ? d.weightStepGrams() : null,
                    d.taxCategoryId(),
                    id);
            audit(branch, id, "PRODUCT_DETAILS", old, d);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceService.class,
                    "editDetails(long,long,Details)");
        }
    }

    /**
     * Creates the operation.
     *
     * @param branch the branch
     * @param d the d
     * @param branches the branches
     * @return the create result
     */
    @Transactional
    public long create(long branch, Details d, List<Long> branches) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceService.class, "create(long,Details,List<Long>)");
        try {
            authorize(branch, false);
            validate(d);
            if (branches == null
                    || branches.isEmpty()
                    || branches.size() > 50
                    || !branches.contains(branch))
                throw new IllegalArgumentException(
                        "Assign the selected branch and up to 50 permitted branches.");
            for (long b : new TreeSet<>(branches)) {
                staff.requireBranchAccess(b);
                if (!Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM branches WHERE id=? AND active=true)",
                                Boolean.class,
                                b))) throw new IllegalArgumentException("Branch not active.");
            }
            Long id =
                    jdbc.queryForObject(
                            "INSERT INTO"
                                + " products(code,category_id,name,description,base_price,sale_mode,minimum_weight_grams,weight_step_grams,active,tax_category_id,created_at,updated_at)"
                                + " VALUES (?,?,?,?,?,?,?,?,true,?,now(),now()) RETURNING id",
                            Long.class,
                            d.code().trim(),
                            d.categoryId(),
                            d.name().trim(),
                            d.description(),
                            d.basePrice(),
                            d.saleMode().name(),
                            d.saleMode() == ProductSaleMode.WEIGHT ? d.minimumWeightGrams() : null,
                            d.saleMode() == ProductSaleMode.WEIGHT ? d.weightStepGrams() : null,
                            d.taxCategoryId());
            for (long b : new TreeSet<>(branches))
                jdbc.update(
                        "INSERT INTO"
                            + " branch_products(branch_id,product_id,available,display_order,created_at,updated_at)"
                            + " VALUES (?,?,false,0,now(),now())",
                        b,
                        id);
            audit(branch, id, "CREATE_PRODUCT", null, d);
            return id;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceService.class,
                    "create(long,Details,List<Long>)");
        }
    }

    /**
     * Edits branch.
     *
     * @param branch the branch
     * @param id the id
     * @param d the d
     */
    @Transactional
    public void editBranch(long branch, long id, BranchEdit d) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceService.class, "editBranch(long,long,BranchEdit)");
        try {
            authorize(branch, false);
            if (Boolean.TRUE.equals(d.clearPriceOverride()) && d.priceOverride() != null)
                throw new IllegalArgumentException("Reset and override cannot be combined.");
            var rows =
                    jdbc.queryForList(
                            "SELECT * FROM branch_products WHERE branch_id=? AND product_id=? FOR"
                                    + " UPDATE",
                            branch,
                            id);
            if (rows.isEmpty())
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not assigned to branch.");
            var old = rows.getFirst();
            if (((Number) old.get("workspace_version")).longValue() != d.version()) conflict();
            jdbc.update(
                    "UPDATE branch_products SET available=COALESCE(?,available),price_override=CASE"
                        + " WHEN ? THEN NULL ELSE COALESCE(?,price_override) END,updated_at=now()"
                        + " WHERE branch_id=? AND product_id=?",
                    d.available(),
                    Boolean.TRUE.equals(d.clearPriceOverride()),
                    d.priceOverride(),
                    branch,
                    id);
            audit(branch, id, "BRANCH_UPDATE", old, d);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceService.class,
                    "editBranch(long,long,BranchEdit)");
        }
    }

    /**
     * Deletes branch item.
     *
     * @param branch the branch
     * @param id the id
     * @param version the version
     */
    @Transactional
    public void deleteBranchItem(long branch, long id, long version) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceService.class, "deleteBranchItem(long,long,long)");
        try {
            authorize(branch, true);
            staff.requirePermission(PermissionName.INVENTORY_MANAGE);
            var rows =
                    jdbc.queryForList(
                            "SELECT * FROM branch_products WHERE branch_id=? AND product_id=? FOR"
                                    + " UPDATE",
                            branch,
                            id);
            if (rows.isEmpty())
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not assigned to branch.");
            var old = rows.getFirst();
            if (((Number) old.get("workspace_version")).longValue() != version) conflict();
            long bp = ((Number) old.get("id")).longValue();
            for (String table :
                    List.of(
                            "inventory_daily_allocations",
                            "inventory_stock_transactions",
                            "inventory_allocation_plan_audit",
                            "inventory_automation_run_items")) {
                if (Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM "
                                        + table
                                        + " WHERE branch_product_id=?)",
                                Boolean.class,
                                bp)))
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "This item has inventory or reservation history. Set it unavailable"
                                    + " instead to preserve existing orders and stock records.");
            }
            if (Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM menu_service_items WHERE"
                                    + " requires_branch_product_id=?)",
                            Boolean.class,
                            bp)))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Another item depends on this item. Remove its service dependency before"
                                + " deleting.");
            // Lock the grouping revision before changing choices, like the grouping editor.
            jdbc.update(
                    "INSERT INTO mobile_menu_config(branch_id,version) VALUES (?,0) ON CONFLICT DO"
                            + " NOTHING",
                    branch);
            jdbc.queryForObject(
                    "SELECT version FROM mobile_menu_config WHERE branch_id=? FOR UPDATE",
                    Long.class,
                    branch);
            jdbc.update(
                    "DELETE FROM mobile_menu_choices WHERE branch_id=? AND product_id=?",
                    branch,
                    id);
            jdbc.update(
                    "DELETE FROM mobile_menu_groups g WHERE g.branch_id=? AND (SELECT count(*) FROM"
                            + " mobile_menu_choices c WHERE c.branch_id=g.branch_id AND"
                            + " c.group_key=g.group_key)<2",
                    branch);
            jdbc.update(
                    "UPDATE mobile_menu_config SET version=version+1 WHERE branch_id=?", branch);
            jdbc.update("DELETE FROM menu_service_items WHERE branch_product_id=?", bp);
            jdbc.update("DELETE FROM inventory_automation_rules WHERE branch_product_id=?", bp);
            jdbc.update("DELETE FROM branch_inventory_policies WHERE branch_product_id=?", bp);
            jdbc.update("DELETE FROM branch_products WHERE id=?", bp);
            audit(branch, id, "DELETE_BRANCH_ITEM", old, null);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceService.class,
                    "deleteBranchItem(long,long,long)");
        }
    }

    /**
     * Images the operation.
     *
     * @param branch the branch
     * @param id the id
     * @param version the version
     * @param image the image
     * @param remove the remove
     * @return the image result
     */
    @Transactional
    public String image(long branch, long id, long version, MultipartFile image, boolean remove) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceService.class, "image(long,long,long,MultipartFile,boolean)");
        try {
            shared(branch, id);
            var old = product(id, true);
            if (((Number) old.get("workspace_version")).longValue() != version) conflict();
            var saved = remove ? products.removeImage(id) : products.uploadImage(id, image);
            audit(branch, id, "PRODUCT_IMAGE", old.get("image_url"), saved.imageUrl());
            return saved.imageUrl();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceService.class,
                    "image(long,long,long,MultipartFile,boolean)");
        }
    }

    /** Immutable dietary edit data contract. */
    public record DietaryEdit(@NotNull @Min(0) Long version, @NotNull Boolean vegetarian) {}

    /**
     * Edits dietary.
     *
     * @param branch the branch
     * @param id the id
     * @param edit the edit
     */
    @Transactional
    public void editDietary(long branch, long id, DietaryEdit edit) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceService.class, "editDietary(long,long,DietaryEdit)");
        try {
            shared(branch, id);
            if (edit.version() == null || edit.version() < 0 || edit.vegetarian() == null)
                throw new IllegalArgumentException(
                        "Choose Veg or Non-veg and a valid item version.");
            var old = product(id, true);
            if (((Number) old.get("workspace_version")).longValue() != edit.version()) conflict();
            jdbc.update(
                    "UPDATE products SET vegetarian=?,updated_at=now() WHERE id=?",
                    edit.vegetarian(),
                    id);
            audit(branch, id, "PRODUCT_DIETARY", old.get("vegetarian"), edit.vegetarian());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceService.class,
                    "editDietary(long,long,DietaryEdit)");
        }
    }

    /** Conflicts the operation. */
    private void conflict() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MenuWorkspaceService.class, "conflict()");
        try {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This item changed since you opened it. Close and reload the item before"
                            + " saving; your entries have been retained.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MenuWorkspaceService.class, "conflict()");
        }
    }

    /** Immutable stock edit data contract. */
    public record StockEdit(
            @Min(0) Long version,
            @Min(0) Long policyVersion,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @jakarta.validation.Valid AdminAllocationApprovalRequest allocation,
            @jakarta.validation.Valid AdminInventoryPolicyRequest policy,
            @jakarta.validation.Valid AdminReadinessUpdateRequest readiness) {}

    /**
     * Stocks the operation.
     *
     * @param branch the branch
     * @param product the product
     * @param date the date
     * @param edit the edit
     */
    @Transactional
    public void stock(long branch, long product, LocalDate date, StockEdit edit) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MenuWorkspaceService.class, "stock(long,long,LocalDate,StockEdit)");
        try {
            authorize(branch, true);
            staff.requirePermission(PermissionName.INVENTORY_MANAGE);
            var ids =
                    jdbc.queryForList(
                            "SELECT id FROM branch_products WHERE branch_id=? AND product_id=? FOR"
                                    + " UPDATE",
                            Long.class,
                            branch,
                            product);
            if (ids.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not in branch.");
            long bp = ids.getFirst();
            var old =
                    jdbc.queryForList(
                            "SELECT * FROM inventory_daily_allocations WHERE branch_product_id=?"
                                    + " AND service_date=? FOR UPDATE",
                            bp,
                            date);
            if (old.isEmpty()
                    ? edit.version() != null
                    : edit.version() == null
                            || ((Number) old.getFirst().get("version")).longValue()
                                    != edit.version()) conflict();
            if (edit.policy() != null) {
                var existing =
                        jdbc.queryForList(
                                "SELECT version FROM branch_inventory_policies WHERE"
                                        + " branch_product_id=? FOR UPDATE",
                                Long.class,
                                bp);
                if (existing.isEmpty()
                        ? edit.policyVersion() != null
                        : edit.policyVersion() == null
                                || !existing.getFirst().equals(edit.policyVersion())) conflict();
                inventory.upsertPolicy(bp, edit.policy());
            }
            var i = edit.allocation();
            var saved =
                    inventory.adjustAllocation(
                            bp,
                            date,
                            new AdminAllocationApprovalRequest(
                                    i.approvedQuantity(),
                                    i.safetyBufferQuantity(),
                                    i.forecastQuantity(),
                                    i.forecastConfidence(),
                                    i.expectedReadyAt(),
                                    edit.reason()),
                            staff.getCurrentStaff().getUsername());
            if (edit.readiness() != null) {
                var ready = edit.readiness();
                saved =
                        inventory.updateReadiness(
                                bp,
                                date,
                                new AdminReadinessUpdateRequest(
                                        ready.status(),
                                        ready.readyQuantity(),
                                        ready.expectedReadyAt(),
                                        edit.reason()),
                                staff.getCurrentStaff().getUsername());
            }
            audit(
                    branch,
                    product,
                    "INVENTORY_ADJUSTMENT",
                    old.isEmpty() ? null : old.getFirst(),
                    saved);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MenuWorkspaceService.class,
                    "stock(long,long,LocalDate,StockEdit)");
        }
    }
}
