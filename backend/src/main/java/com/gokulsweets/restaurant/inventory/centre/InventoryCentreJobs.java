package com.gokulsweets.restaurant.inventory.centre;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Backend inventory centre jobs contract and implementation. */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryCentreJobs {

    private final JdbcTemplate jdbc;

    private final ObjectMapper mapper;

    private final StaffAuthorizationService staff;

    private final Clock inventoryClock;

    /**
     * Immutable entry data contract.
     *
     * @param productId the product id
     * @param branchVersion the branch version
     * @param policyVersion the policy version
     * @param allocationVersion the allocation version
     * @param saleMode the sale mode
     * @param quantity the quantity
     * @param readyQuantity the ready quantity
     */
    public record Entry(
            @NotNull @Positive Long productId,
            @NotNull @Min(0) Long branchVersion,
            @Min(0) Long policyVersion,
            @Min(0) Long allocationVersion,
            @NotBlank @Pattern(regexp = "UNIT|WEIGHT") String saleMode,
            @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
            @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal readyQuantity) {}

    /**
     * Immutable options data contract.
     *
     * @param fromDate the from date
     * @param throughDate the through date
     * @param method the method
     * @param applyInventory the apply inventory
     * @param applyPolicy the apply policy
     * @param openPurchases the open purchases
     * @param applyHours the apply hours
     * @param opens the opens
     * @param closes the closes
     * @param weekdays the weekdays
     * @param enableHours the enable hours
     * @param reason the reason
     */
    public record Options(
            @NotNull LocalDate fromDate,
            @NotNull LocalDate throughDate,
            @NotBlank @Pattern(regexp = "DAILY_PRODUCTION|READY_STOCK|MANUAL") String method,
            @NotNull Boolean applyInventory,
            @NotNull Boolean applyPolicy,
            @NotNull Boolean openPurchases,
            @NotNull Boolean applyHours,
            LocalTime opens,
            LocalTime closes,
            @Min(1) @Max(127) int weekdays,
            @NotNull Boolean enableHours,
            @NotBlank @Size(max = 500) String reason) {}

    /**
     * Immutable submit data contract.
     *
     * @param submissionId the submission id
     * @param options the options
     * @param items the items
     */
    public record Submit(
            @NotNull UUID submissionId,
            @NotNull @Valid Options options,
            @NotNull @Size(min = 1, max = 500) List<@NotNull @Valid Entry> items) {}

    /**
     * Immutable date version data contract.
     *
     * @param date the date
     * @param version the version
     */
    public record DateVersion(LocalDate date, Long version) {}

    /**
     * Immutable timing data contract.
     *
     * @param opens the opens
     * @param closes the closes
     * @param weekdays the weekdays
     * @param soldOut the sold out
     * @param dependency the dependency
     */
    public record Timing(
            LocalTime opens, LocalTime closes, int weekdays, boolean soldOut, Long dependency) {}

    /**
     * Immutable work data contract.
     *
     * @param options the options
     * @param entry the entry
     * @param dates the dates
     * @param groupVersion the group version
     * @param timing the timing
     */
    public record Work(
            Options options,
            Entry entry,
            List<DateVersion> dates,
            long groupVersion,
            Timing timing) {}

    /**
     * Actors name.
     *
     * @return the actor name result
     */
    public String actorName() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreJobs.class, "actorName()");
        try {
            return staff.getCurrentStaff().getUsername();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryCentreJobs.class, "actorName()");
        }
    }

    /**
     * Authorizes the operation.
     *
     * @param branch the branch
     * @param manage the manage
     */
    public void authorize(long branch, boolean manage) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreJobs.class, "authorize(long,boolean)");
        try {
            staff.requirePermission(PermissionName.MENU_MANAGE);
            staff.requirePermission(
                    manage ? PermissionName.INVENTORY_MANAGE : PermissionName.INVENTORY_VIEW);
            staff.requireBranchAccess(branch);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryCentreJobs.class,
                    "authorize(long,boolean)");
        }
    }

    /**
     * Submits the operation.
     *
     * @param branch the branch
     * @param input the input
     * @return the submit result
     */
    @Transactional
    public Map<String, Object> submit(long branch, Submit input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreJobs.class, "submit(long,Submit)");
        try {
            authorize(branch, true);
            // Recover identical submissions before applying date rules that can change overnight.
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(714187)", Object.class);
            long actor = staff.getCurrentStaff().getId();
            String digest;
            try {
                digest =
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(
                                                        mapper.writeValueAsString(input)
                                                                .getBytes(StandardCharsets.UTF_8)));
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to serialize inventory request.", failure);
            }
            var previous =
                    jdbc.queryForList(
                            "SELECT * FROM inventory_centre_jobs WHERE id=?", input.submissionId());
            if (!previous.isEmpty()) {
                var row = previous.getFirst();
                if (((Number) row.get("branch_id")).longValue() != branch
                        || ((Number) row.get("staff_id")).longValue() != actor
                        || !digest.equals(row.get("digest")))
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Submission identifier is already used for a different plan.");
                return summary(branch, input.submissionId());
            }
            var today = LocalDate.now(inventoryClock);
            var o = input.options();
            long days =
                    java.time.temporal.ChronoUnit.DAYS.between(o.fromDate(), o.throughDate()) + 1;
            if (o.fromDate().isBefore(today)
                    || o.throughDate().isAfter(today.plusDays(60))
                    || days < 1
                    || days > 60
                    || days * input.items().size() > 10000)
                throw new IllegalArgumentException(
                        "Choose current/future dates, at most 60 days and 10,000 item-date"
                                + " allocations per job.");
            if (o.applyHours()
                    && (o.opens() == null || o.closes() == null || o.opens().equals(o.closes())))
                throw new IllegalArgumentException(
                        "Enter different opening and closing times in IST.");
            if (o.applyInventory() && input.items().stream().anyMatch(e -> e.quantity() == null))
                throw new IllegalArgumentException(
                        "Enter allocation quantities for inventory changes.");
            if (!o.applyInventory() && !o.applyHours())
                throw new IllegalArgumentException("Select inventory or service-hour changes.");
            if (!o.applyInventory()
                    && input.items().stream().anyMatch(e -> e.readyQuantity() != null))
                throw new IllegalArgumentException("Hours-only plans do not confirm stock.");
            if (!o.method().equals("READY_STOCK")
                    && input.items().stream().anyMatch(e -> e.readyQuantity() != null))
                throw new IllegalArgumentException(
                        "Daily capacity does not confirm prepared stock.");
            if (!o.fromDate().equals(today)
                    && input.items().stream().anyMatch(e -> e.readyQuantity() != null))
                throw new IllegalArgumentException(
                        "Future stock cannot be confirmed physically ready.");
            var ids = new HashSet<Long>();
            for (var e : input.items())
                if (!ids.add(e.productId()))
                    throw new IllegalArgumentException("Select each product once.");
            if (jdbc.queryForObject(
                            "SELECT count(*) FROM inventory_centre_jobs WHERE"
                                    + " succeeded+failed<total",
                            Long.class)
                    >= 3)
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Three inventory jobs are active. Wait for completion before submitting"
                                + " another.");
            if (jdbc.queryForObject(
                            "SELECT count(*) FROM inventory_centre_jobs WHERE branch_id=? AND"
                                    + " succeeded+failed<total",
                            Long.class,
                            branch)
                    > 0)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "This branch already has an active inventory job.");
            // Resolve stock versions in a single bounded query rather than one query per date.
            var named = new NamedParameterJdbcTemplate(jdbc);
            var args =
                    Map.<String, Object>of(
                            "branch",
                            branch,
                            "ids",
                            ids,
                            "from",
                            o.fromDate(),
                            "through",
                            o.throughDate());
            var branches =
                    named.queryForList(
                            "SELECT bp.id,bp.product_id,bp.workspace_version,q.sale_mode,p.version"
                                + " AS policy_version FROM branch_products bp JOIN products q ON"
                                + " q.id=bp.product_id LEFT JOIN branch_inventory_policies p ON"
                                + " p.branch_product_id=bp.id WHERE bp.branch_id=:branch AND"
                                + " bp.product_id IN (:ids)",
                            args);
            var byProduct = new HashMap<Long, Map<String, Object>>();
            branches.forEach(r -> byProduct.put(((Number) r.get("product_id")).longValue(), r));
            if (byProduct.size() != ids.size())
                throw new IllegalArgumentException("Some items do not belong to this branch.");
            // A group is never collapsed into one stock quantity. All selected variants must have
            // their own reviewed row.
            var versions = new HashMap<String, Long>();
            named.queryForList(
                            "SELECT a.branch_product_id,a.service_date,a.version FROM"
                                + " inventory_daily_allocations a JOIN branch_products bp ON"
                                + " bp.id=a.branch_product_id WHERE bp.branch_id=:branch AND"
                                + " bp.product_id IN (:ids) AND a.service_date BETWEEN :from AND"
                                + " :through",
                            args)
                    .forEach(
                            r ->
                                    versions.put(
                                            r.get("branch_product_id")
                                                    + ":"
                                                    + r.get("service_date"),
                                            ((Number) r.get("version")).longValue()));
            long groupVersion =
                    jdbc.queryForObject(
                            "SELECT COALESCE((SELECT version FROM mobile_menu_config WHERE"
                                    + " branch_id=?),0)",
                            Long.class,
                            branch);
            var timings = new HashMap<Long, Timing>();
            named.query(
                    "SELECT s.* FROM menu_service_items s JOIN branch_products bp ON"
                        + " bp.id=s.branch_product_id WHERE bp.branch_id=:branch AND bp.product_id"
                        + " IN (:ids)",
                    args,
                    (org.springframework.jdbc.core.RowCallbackHandler)
                            r ->
                                    timings.put(
                                            r.getLong("branch_product_id"),
                                            new Timing(
                                                    r.getObject("starts_at", LocalTime.class),
                                                    r.getObject("ends_at", LocalTime.class),
                                                    r.getInt("weekdays"),
                                                    r.getBoolean("sold_out"),
                                                    (Long)
                                                            r.getObject(
                                                                    "requires_branch_product_id"))));
            var taskRows = new ArrayList<Object[]>();
            jdbc.update(
                    "INSERT INTO inventory_centre_jobs(id,branch_id,staff_id,digest,total) VALUES"
                            + " (?,?,?,?,?)",
                    input.submissionId(),
                    branch,
                    actor,
                    digest,
                    input.items().size());
            for (var e : input.items()) {
                var bp = byProduct.get(e.productId());
                if (o.applyInventory() && !Objects.equals(bp.get("sale_mode"), e.saleMode()))
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "An item selling unit changed. Reload before submitting.");
                if (((Number) bp.get("workspace_version")).longValue() != e.branchVersion()
                        || !Objects.equals(
                                bp.get("policy_version") == null
                                        ? null
                                        : ((Number) bp.get("policy_version")).longValue(),
                                e.policyVersion()))
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "An item policy or branch entry changed. Reload before submitting.");
                var dates = new ArrayList<DateVersion>();
                for (long offset = 0; offset < days; offset++) {
                    var date = o.fromDate().plusDays(offset);
                    dates.add(new DateVersion(date, versions.get(bp.get("id") + ":" + date)));
                }
                if (!Objects.equals(dates.getFirst().version(), e.allocationVersion()))
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "An allocation changed. Reload before submitting.");
                taskRows.add(
                        new Object[] {
                            input.submissionId(),
                            e.productId(),
                            mapper.writeValueAsString(
                                    new Work(
                                            o,
                                            e,
                                            dates,
                                            groupVersion,
                                            timings.get(((Number) bp.get("id")).longValue())))
                        });
            }
            jdbc.batchUpdate(
                    "INSERT INTO inventory_centre_tasks(job_id,product_id,payload) VALUES (?,?,?)",
                    taskRows);
            log.info(
                    "Inventory centre queued: jobId={}, branchId={}, actorId={}, items={},"
                            + " itemDates={}",
                    input.submissionId(),
                    branch,
                    actor,
                    input.items().size(),
                    days * input.items().size());
            return summary(branch, input.submissionId());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryCentreJobs.class, "submit(long,Submit)");
        }
    }

    /**
     * Timings the operation.
     *
     * @param bp the bp
     * @return the timing result
     */
    public Timing timing(long bp) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreJobs.class, "timing(long)");
        try {
            var rows =
                    jdbc.query(
                            "SELECT * FROM menu_service_items WHERE branch_product_id=?",
                            (r, n) ->
                                    new Timing(
                                            r.getObject("starts_at", LocalTime.class),
                                            r.getObject("ends_at", LocalTime.class),
                                            r.getInt("weekdays"),
                                            r.getBoolean("sold_out"),
                                            (Long) r.getObject("requires_branch_product_id")),
                            bp);
            return rows.isEmpty() ? null : rows.getFirst();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryCentreJobs.class, "timing(long)");
        }
    }

    /**
     * Summary the operation.
     *
     * @param branch the branch
     * @param job the job
     * @return the summary result
     */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(long branch, UUID job) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreJobs.class, "summary(long,UUID)");
        try {
            authorize(branch, false);
            var rows =
                    jdbc.queryForList(
                            "SELECT id,total,succeeded,failed,created_at,updated_at FROM"
                                    + " inventory_centre_jobs WHERE branch_id=? AND id=?",
                            branch,
                            job);
            if (rows.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory job not found.");
            return rows.getFirst();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryCentreJobs.class, "summary(long,UUID)");
        }
    }

    /**
     * Recents the operation.
     *
     * @param branch the branch
     * @return the recent result
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> recent(long branch) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreJobs.class, "recent(long)");
        try {
            authorize(branch, false);
            return jdbc.queryForList(
                    "SELECT id,total,succeeded,failed,created_at,updated_at FROM"
                        + " inventory_centre_jobs WHERE branch_id=? ORDER BY created_at DESC LIMIT"
                        + " 10",
                    branch);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryCentreJobs.class, "recent(long)");
        }
    }

    /**
     * Resultses the operation.
     *
     * @param branch the branch
     * @param job the job
     * @param page the page
     * @return the results result
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> results(long branch, UUID job, int page) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreJobs.class, "results(long,UUID,int)");
        try {
            summary(branch, job);
            if (page < 0 || page > 10000)
                throw new IllegalArgumentException("Invalid result page.");
            return jdbc.queryForList(
                    "SELECT t.product_id,p.name,t.status,t.error,t.finished_at FROM"
                            + " inventory_centre_tasks t JOIN products p ON p.id=t.product_id WHERE"
                            + " t.job_id=? ORDER BY t.id LIMIT 50 OFFSET ?",
                    job,
                    (long) page * 50);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryCentreJobs.class, "results(long,UUID,int)");
        }
    }
}
