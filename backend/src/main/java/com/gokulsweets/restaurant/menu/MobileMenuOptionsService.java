package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.branch.BranchRepository;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Coordinates mobile menu options operations. */
@Service
@RequiredArgsConstructor
public class MobileMenuOptionsService {

    private final JdbcTemplate jdbc;

    private final BranchRepository branches;

    private final StaffAuthorizationService staff;

    /**
     * Immutable choice data contract.
     *
     * @param productId the product id
     * @param label the label
     */
    public record Choice(@NotNull Long productId, @NotBlank @Size(max = 30) String label) {}

    /**
     * Immutable group data contract.
     *
     * @param key the key
     * @param title the title
     * @param choices the choices
     */
    public record Group(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,40}") String key,
            @NotBlank @Size(max = 100) String title,
            @NotNull @Size(min = 2, max = 6) List<@Valid Choice> choices) {}

    /**
     * Immutable input data contract.
     *
     * @param version the version
     * @param groups the groups
     */
    public record Input(
            @Min(0) long version, @NotNull @Size(max = 500) List<@Valid Group> groups) {}

    /**
     * Immutable snapshot data contract.
     *
     * @param version the version
     * @param groups the groups
     */
    public record Snapshot(long version, List<Group> groups) {}

    /**
     * Requires menu-management permission and branch access before editing mobile menu option
     * groups.
     *
     * <p>Authorization checks include {@code PermissionName.MENU_MANAGE}.
     *
     * @param id the id supplied to this method
     */
    private void authorize(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsService.class, "authorize(long)");
        try {
            staff.requirePermission(PermissionName.MENU_MANAGE);
            staff.requireBranchAccess(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MobileMenuOptionsService.class, "authorize(long)");
        }
    }

    /**
     * Publics read.
     *
     * @param id the id
     * @return the public read result
     */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Snapshot publicRead(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsService.class, "publicRead(long)");
        try {
            var branch =
                    branches.findById(id)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND, "Branch not found."));
            if (!branch.isActive())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not available.");
            return read(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MobileMenuOptionsService.class, "publicRead(long)");
        }
    }

    /**
     * Admins read.
     *
     * @param id the id
     * @return the admin read result
     */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Snapshot adminRead(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsService.class, "adminRead(long)");
        try {
            authorize(id);
            return read(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MobileMenuOptionsService.class, "adminRead(long)");
        }
    }

    /**
     * Loads the branch's mobile option-group snapshot; callers perform access checks when required.
     *
     * <p>Reads {@code mobile_menu_choices}, {@code mobile_menu_config}, {@code mobile_menu_groups}.
     *
     * @param id the id supplied to this method
     * @return the {@code Snapshot} result
     */
    private Snapshot read(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsService.class, "read(long)");
        try {
            var versions =
                    jdbc.queryForList(
                            "SELECT version FROM mobile_menu_config WHERE branch_id=?",
                            Long.class,
                            id);
            var groups =
                    jdbc.query(
                            "SELECT group_key,title FROM mobile_menu_groups WHERE branch_id=? ORDER"
                                    + " BY position",
                            (rs, n) -> new Group(rs.getString(1), rs.getString(2), List.of()),
                            id);
            Map<String, List<Choice>> choices = new HashMap<>();
            jdbc.query(
                    "SELECT group_key,product_id,label FROM mobile_menu_choices WHERE branch_id=?"
                            + " ORDER BY group_key,position",
                    (org.springframework.jdbc.core.RowCallbackHandler)
                            rs ->
                                    choices.computeIfAbsent(
                                                    rs.getString(1), key -> new ArrayList<>())
                                            .add(new Choice(rs.getLong(2), rs.getString(3))),
                    id);
            var complete =
                    groups.stream()
                            .map(
                                    g ->
                                            new Group(
                                                    g.key(),
                                                    g.title(),
                                                    List.copyOf(
                                                            choices.getOrDefault(
                                                                    g.key(), List.of()))))
                            .toList();
            return new Snapshot(versions.isEmpty() ? 0 : versions.getFirst(), complete);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MobileMenuOptionsService.class, "read(long)");
        }
    }

    /**
     * Validates and replaces mobile menu option groups using the supplied version, returning the
     * saved snapshot.
     *
     * <p>Reads {@code branch_products}, {@code mobile_menu_config}, {@code products}.
     *
     * <p>Writes {@code mobile_menu_choices}, {@code mobile_menu_config}, {@code
     * mobile_menu_groups}.
     *
     * @param id the id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code read(id)}
     * @throws IllegalArgumentException when the method rejects the request with {@code A product or
     *     portion label is repeated.}; {@code Choose a piece-based item from this branch menu for
     *     every portion.}; {@code Each portion group needs a unique key.}; {@code Portions must
     *     belong to the same category.}
     * @throws ResponseStatusException when the method rejects the request with {@code Branch not
     *     found.}; {@code Portion options changed. Reload before saving.}
     */
    @Transactional
    public Snapshot save(long id, Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsService.class, "save(long,Input)");
        try {
            authorize(id);
            if (!branches.existsById(id))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
            // Validation precedes any replacement. Never infer a portion from its product name.
            Set<Long> products = new HashSet<>();
            Set<String> keys = new HashSet<>();
            for (var group : input.groups()) {
                if (!keys.add(group.key()))
                    throw new IllegalArgumentException("Each portion group needs a unique key.");
                Set<String> labels = new HashSet<>();
                Long category = null;
                for (var choice : group.choices()) {
                    if (!products.add(choice.productId())
                            || !labels.add(choice.label().trim().toLowerCase(Locale.ROOT)))
                        throw new IllegalArgumentException(
                                "A product or portion label is repeated.");
                    var categories =
                            jdbc.queryForList(
                                    "SELECT p.category_id FROM branch_products bp JOIN products p"
                                        + " ON p.id=bp.product_id WHERE bp.branch_id=? AND p.id=?"
                                        + " AND p.sale_mode='UNIT'",
                                    Long.class,
                                    id,
                                    choice.productId());
                    if (categories.size() != 1)
                        throw new IllegalArgumentException(
                                "Choose a piece-based item from this branch menu for every"
                                        + " portion.");
                    if (category != null && !category.equals(categories.getFirst()))
                        throw new IllegalArgumentException(
                                "Portions must belong to the same category.");
                    category = categories.getFirst();
                }
            }
            jdbc.update(
                    "INSERT INTO mobile_menu_config(branch_id) VALUES (?) ON CONFLICT DO NOTHING",
                    id);
            long current =
                    jdbc.queryForObject(
                            "SELECT version FROM mobile_menu_config WHERE branch_id=? FOR UPDATE",
                            Long.class,
                            id);
            if (current != input.version())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Portion options changed. Reload before saving.");
            jdbc.update("DELETE FROM mobile_menu_groups WHERE branch_id=?", id);
            for (int i = 0; i < input.groups().size(); i++) {
                var group = input.groups().get(i);
                jdbc.update(
                        "INSERT INTO mobile_menu_groups(branch_id,group_key,title,position) VALUES"
                                + " (?,?,?,?)",
                        id,
                        group.key(),
                        group.title().trim(),
                        i);
                for (int j = 0; j < group.choices().size(); j++) {
                    var c = group.choices().get(j);
                    jdbc.update(
                            "INSERT INTO"
                                + " mobile_menu_choices(branch_id,group_key,product_id,label,position)"
                                + " VALUES (?,?,?,?,?)",
                            id,
                            group.key(),
                            c.productId(),
                            c.label().trim(),
                            j);
                }
            }
            jdbc.update("UPDATE mobile_menu_config SET version=version+1 WHERE branch_id=?", id);
            return read(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, MobileMenuOptionsService.class, "save(long,Input)");
        }
    }

    /**
     * Immutable group match data contract.
     *
     * @param version the version
     * @param group the group
     */
    public record GroupMatch(long version, Group group) {}

    /**
     * Fors product.
     *
     * @param branch the branch
     * @param product the product
     * @return the for product result
     */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public GroupMatch forProduct(long branch, long product) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsService.class, "forProduct(long,long)");
        try {
            authorize(branch);
            var vs =
                    jdbc.queryForList(
                            "SELECT version FROM mobile_menu_config WHERE branch_id=?",
                            Long.class,
                            branch);
            var gs =
                    jdbc.query(
                            "SELECT g.group_key,g.title FROM mobile_menu_groups g JOIN"
                                + " mobile_menu_choices c ON c.branch_id=g.branch_id AND"
                                + " c.group_key=g.group_key WHERE g.branch_id=? AND c.product_id=?",
                            (rs, n) -> new Group(rs.getString(1), rs.getString(2), List.of()),
                            branch,
                            product);
            if (gs.isEmpty()) return new GroupMatch(vs.isEmpty() ? 0 : vs.getFirst(), null);
            var g = gs.getFirst();
            return new GroupMatch(
                    vs.getFirst(),
                    new Group(
                            g.key(),
                            g.title(),
                            jdbc.query(
                                    "SELECT product_id,label FROM mobile_menu_choices WHERE"
                                            + " branch_id=? AND group_key=? ORDER BY position",
                                    (rs, n) -> new Choice(rs.getLong(1), rs.getString(2)),
                                    branch,
                                    g.key())));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MobileMenuOptionsService.class,
                    "forProduct(long,long)");
        }
    }

    /**
     * Immutable group page data contract.
     *
     * @param version the version
     * @param groups the groups
     * @param total the total
     * @param page the page
     * @param totalPages the total pages
     */
    public record GroupPage(
            long version, List<Group> groups, long total, int page, int totalPages) {}

    /**
     * Returns a bounded page of matching mobile option groups within one repeatable-read snapshot
     * after checking administrative access.
     *
     * <p>Reads {@code mobile_menu_choices}, {@code mobile_menu_config}, {@code mobile_menu_groups}.
     *
     * @param branch the branch supplied to this method
     * @param search the search supplied to this method
     * @param page the page supplied to this method
     * @return the {@code GroupPage} result
     * @throws IllegalArgumentException when the method rejects the request with {@code Invalid
     *     group search.}
     */
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public GroupPage page(long branch, String search, int page) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(MobileMenuOptionsService.class, "page(long,String,int)");
        try {
            authorize(branch);
            if (page < 0 || page > 10000 || search.length() > 100)
                throw new IllegalArgumentException("Invalid group search.");
            var versions =
                    jdbc.queryForList(
                            "SELECT version FROM mobile_menu_config WHERE branch_id=?",
                            Long.class,
                            branch);
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            long total =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM mobile_menu_groups WHERE branch_id=? AND"
                                    + " lower(title) LIKE ?",
                            Long.class,
                            branch,
                            pattern);
            var groups =
                    jdbc.query(
                            "SELECT group_key,title FROM mobile_menu_groups WHERE branch_id=? AND"
                                + " lower(title) LIKE ? ORDER BY position,group_key LIMIT 25 OFFSET"
                                + " ?",
                            (rs, n) -> new Group(rs.getString(1), rs.getString(2), List.of()),
                            branch,
                            pattern,
                            page * 25);
            var complete =
                    groups.stream()
                            .map(
                                    g ->
                                            new Group(
                                                    g.key(),
                                                    g.title(),
                                                    jdbc.query(
                                                            "SELECT product_id,label FROM"
                                                                    + " mobile_menu_choices WHERE"
                                                                    + " branch_id=? AND group_key=?"
                                                                    + " ORDER BY position",
                                                            (rs, n) ->
                                                                    new Choice(
                                                                            rs.getLong(1),
                                                                            rs.getString(2)),
                                                            branch,
                                                            g.key())))
                            .toList();
            return new GroupPage(
                    versions.isEmpty() ? 0 : versions.getFirst(),
                    complete,
                    total,
                    page,
                    (int) ((total + 24) / 25));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MobileMenuOptionsService.class,
                    "page(long,String,int)");
        }
    }

    /**
     * Saves one.
     *
     * @param branch the branch
     * @param version the version
     * @param group the group
     * @param remove the remove
     * @return the save one result
     */
    @Transactional
    public long saveOne(long branch, long version, Group group, boolean remove) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        MobileMenuOptionsService.class, "saveOne(long,long,Group,boolean)");
        try {
            authorize(branch);
            jdbc.update(
                    "INSERT INTO mobile_menu_config(branch_id) VALUES (?) ON CONFLICT DO NOTHING",
                    branch);
            long current =
                    jdbc.queryForObject(
                            "SELECT version FROM mobile_menu_config WHERE branch_id=? FOR UPDATE",
                            Long.class,
                            branch);
            if (current != version)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Groups changed. Reload before saving; your draft is retained.");
            if (!remove) {
                Set<Long> ids = new HashSet<>();
                Set<String> labels = new HashSet<>();
                Long category = null;
                for (var c : group.choices()) {
                    if (!ids.add(c.productId())
                            || !labels.add(c.label().trim().toLowerCase(Locale.ROOT)))
                        throw new IllegalArgumentException("Product or label repeated.");
                    var cats =
                            jdbc.queryForList(
                                    "SELECT p.category_id FROM products p JOIN branch_products bp"
                                        + " ON bp.product_id=p.id WHERE bp.branch_id=? AND p.id=?"
                                        + " AND p.sale_mode='UNIT'",
                                    Long.class,
                                    branch,
                                    c.productId());
                    if (cats.size() != 1)
                        throw new IllegalArgumentException(
                                "Choose count-based products assigned to this branch.");
                    if (category != null && !category.equals(cats.getFirst()))
                        throw new IllegalArgumentException("Options must share a category.");
                    category = cats.getFirst();
                    if (jdbc.queryForObject(
                                    "SELECT count(*) FROM mobile_menu_choices WHERE branch_id=? AND"
                                            + " product_id=? AND group_key<>?",
                                    Long.class,
                                    branch,
                                    c.productId(),
                                    group.key())
                            > 0)
                        throw new IllegalArgumentException(
                                "This product belongs to another group.");
                }
                if (jdbc.queryForObject(
                                "SELECT count(*) FROM mobile_menu_groups WHERE branch_id=? AND"
                                        + " group_key<>?",
                                Long.class,
                                branch,
                                group.key())
                        >= 500)
                    throw new IllegalArgumentException("Maximum 500 groups per branch.");
            }
            var before =
                    jdbc.queryForList(
                            "SELECT * FROM mobile_menu_choices WHERE branch_id=? AND group_key=?",
                            branch,
                            group.key());
            if (remove)
                jdbc.update(
                        "DELETE FROM mobile_menu_groups WHERE branch_id=? AND group_key=?",
                        branch,
                        group.key());
            else {
                jdbc.update(
                        "INSERT INTO mobile_menu_groups(branch_id,group_key,title,position) VALUES"
                            + " (?,?,?,COALESCE((SELECT max(position)+1 FROM mobile_menu_groups"
                            + " WHERE branch_id=?),0)) ON CONFLICT(branch_id,group_key) DO UPDATE"
                            + " SET title=EXCLUDED.title",
                        branch,
                        group.key(),
                        group.title().trim(),
                        branch);
                jdbc.update(
                        "DELETE FROM mobile_menu_choices WHERE branch_id=? AND group_key=?",
                        branch,
                        group.key());
                for (int i = 0; i < group.choices().size(); i++) {
                    var c = group.choices().get(i);
                    jdbc.update(
                            "INSERT INTO"
                                + " mobile_menu_choices(branch_id,group_key,product_id,label,position)"
                                + " VALUES (?,?,?,?,?)",
                            branch,
                            group.key(),
                            c.productId(),
                            c.label().trim(),
                            i);
                }
            }
            jdbc.update(
                    "INSERT INTO"
                        + " menu_workspace_audit(actor,branch_id,action,before_state,after_state)"
                        + " VALUES (?,?,'GROUP',?,?)",
                    staff.getCurrentStaff().getUsername(),
                    branch,
                    before.toString(),
                    remove ? "Ungrouped" : group.toString());
            jdbc.update(
                    "UPDATE mobile_menu_config SET version=version+1 WHERE branch_id=?", branch);
            return current + 1;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    MobileMenuOptionsService.class,
                    "saveOne(long,long,Group,boolean)");
        }
    }
}
