package com.gokulsweets.restaurant.branch;

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

import java.util.List;

/** Coordinates branch offerings operations. */
@Service
@RequiredArgsConstructor
public class BranchOfferingsService {

    private final JdbcTemplate jdbc;

    private final BranchRepository branches;

    private final StaffAuthorizationService staff;

    /**
     * Immutable offering data contract.
     *
     * @param title the title
     * @param description the description
     */
    public record Offering(
            @NotBlank @Size(max = 80) String title,
            @NotBlank @Size(max = 240) String description) {}

    /**
     * Immutable input data contract.
     *
     * @param offerings the offerings
     */
    public record Input(@NotNull @Size(max = 12) List<@Valid Offering> offerings) {}

    /**
     * Immutable snapshot data contract.
     *
     * @param version the version
     * @param draft the draft
     * @param published the published
     */
    public record Snapshot(long version, List<Offering> draft, List<Offering> published) {}

    /**
     * Authorizes the operation.
     *
     * @param id the id
     */
    private void authorize(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsService.class, "authorize(long)");
        try {
            staff.requirePermission(PermissionName.BRANCH_MANAGE);
            staff.requireBranchAccess(id);
            if (!branches.existsById(id))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchOfferingsService.class, "authorize(long)");
        }
    }

    /**
     * Lists the operation.
     *
     * @param id the id
     * @param scope the scope
     * @return the list result
     */
    private List<Offering> list(long id, String scope) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsService.class, "list(long,String)");
        try {
            return jdbc.query(
                    "SELECT title, description FROM branch_offerings WHERE branch_id=? AND scope=?"
                            + " ORDER BY position",
                    (rs, n) -> new Offering(rs.getString(1), rs.getString(2)),
                    id,
                    scope);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchOfferingsService.class, "list(long,String)");
        }
    }

    /**
     * Versions the operation.
     *
     * @param id the id
     * @param lock the lock
     * @return the version result
     */
    private long version(long id, boolean lock) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsService.class, "version(long,boolean)");
        try {
            jdbc.update(
                    "INSERT INTO branch_offering_settings(branch_id) VALUES (?) ON CONFLICT DO"
                            + " NOTHING",
                    id);
            return jdbc.queryForObject(
                    "SELECT edit_version FROM branch_offering_settings WHERE branch_id=?"
                            + (lock ? " FOR UPDATE" : ""),
                    Long.class,
                    id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchOfferingsService.class,
                    "version(long,boolean)");
        }
    }

    /**
     * Snapshots the operation.
     *
     * @param id the id
     * @return the snapshot result
     */
    private Snapshot snapshot(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsService.class, "snapshot(long)");
        try {
            return new Snapshot(version(id, false), list(id, "DRAFT"), list(id, "PUBLISHED"));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchOfferingsService.class, "snapshot(long)");
        }
    }

    /**
     * Expecteds the operation.
     *
     * @param id the id
     * @param expected the expected
     */
    private void expected(long id, long expected) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsService.class, "expected(long,long)");
        try {
            if (version(id, true) != expected)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Branch offerings changed. Reload before saving.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchOfferingsService.class, "expected(long,long)");
        }
    }

    /**
     * Replaces the operation.
     *
     * @param id the id
     * @param scope the scope
     * @param items the items
     */
    private void replace(long id, String scope, List<Offering> items) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BranchOfferingsService.class, "replace(long,String,List<Offering>)");
        try {
            jdbc.update("DELETE FROM branch_offerings WHERE branch_id=? AND scope=?", id, scope);
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i);
                jdbc.update(
                        "INSERT INTO branch_offerings(branch_id,scope,position,title,description)"
                                + " VALUES (?,?,?,?,?)",
                        id,
                        scope,
                        i,
                        item.title().trim(),
                        item.description().trim());
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchOfferingsService.class,
                    "replace(long,String,List<Offering>)");
        }
    }

    /**
     * Admins the operation.
     *
     * @param id the id
     * @return the admin result
     */
    @Transactional
    public Snapshot admin(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsService.class, "admin(long)");
        try {
            authorize(id);
            return snapshot(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchOfferingsService.class, "admin(long)");
        }
    }

    /**
     * Saves the operation.
     *
     * @param id the id
     * @param input the input
     * @param expected the expected
     * @return the save result
     */
    @Transactional
    public Snapshot save(long id, Input input, long expected) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsService.class, "save(long,Input,long)");
        try {
            authorize(id);
            expected(id, expected);
            if (input.offerings() == null
                    || input.offerings().size() > 12
                    || input.offerings().stream()
                            .anyMatch(
                                    o ->
                                            o == null
                                                    || o.title() == null
                                                    || o.title().isBlank()
                                                    || o.title().length() > 80
                                                    || o.description() == null
                                                    || o.description().isBlank()
                                                    || o.description().length() > 240))
                throw new IllegalArgumentException(
                        "Add up to twelve offerings with a title and description.");
            replace(id, "DRAFT", input.offerings());
            jdbc.update(
                    "UPDATE branch_offering_settings SET edit_version=edit_version+1 WHERE"
                            + " branch_id=?",
                    id);
            return snapshot(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchOfferingsService.class,
                    "save(long,Input,long)");
        }
    }

    /**
     * Publishes the operation.
     *
     * @param id the id
     * @param expected the expected
     * @return the publish result
     */
    @Transactional
    public Snapshot publish(long id, long expected) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsService.class, "publish(long,long)");
        try {
            authorize(id);
            expected(id, expected);
            replace(id, "PUBLISHED", list(id, "DRAFT"));
            jdbc.update(
                    "UPDATE branch_offering_settings SET edit_version=edit_version+1 WHERE"
                            + " branch_id=?",
                    id);
            return snapshot(id);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchOfferingsService.class, "publish(long,long)");
        }
    }

    /**
     * Publisheds the operation.
     *
     * @param id the id
     * @return the published result
     */
    @Transactional(readOnly = true)
    public List<Offering> published(long id) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchOfferingsService.class, "published(long)");
        try {
            return list(id, "PUBLISHED");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchOfferingsService.class, "published(long)");
        }
    }
}
