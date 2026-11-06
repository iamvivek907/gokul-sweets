package com.gokulsweets.restaurant.inventory.rolling;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.dto.AdminAllocationApprovalRequest;
import com.gokulsweets.restaurant.inventory.dto.AdminReadinessUpdateRequest;
import com.gokulsweets.restaurant.inventory.enums.InventoryAllocationStatus;
import com.gokulsweets.restaurant.inventory.service.AdminInventoryService;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** Opt-in rolling schedules; creates missing records without rewriting existing stock or slots. */
@Service
@RequiredArgsConstructor
public class RollingScheduleService {

    private final JdbcTemplate jdbc;

    private final TransactionTemplate transactions;

    private final AdminInventoryService inventory;

    private final Clock clock;

    private final EnhancementProperties features;

    private final RollingAutomationDiagnostics diagnostics;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Existing regular interval whose time range must remain untouched during gap repair. */
    private record SlotInterval(LocalTime start, LocalTime end) {}

    /** Recovery outcomes distinguish untouched stock from incompatible historical units. */
    private enum ReadinessRecovery {
        PUBLISHED,
        PRESERVED,
        UNIT_MISMATCH
    }

    /** Explicit inventory template tied to an existing policy in this branch. */
    public record Template(long policyId, BigDecimal quantity, boolean autoApprove) {}

    /** Independent schedule configuration; maintainDays includes today. */
    public record Plan(
            boolean enabled,
            LocalTime openingTime,
            LocalTime closingTime,
            int slotMinutes,
            int capacity,
            int maintainDays,
            int weekdays,
            long version,
            List<Template> templates,
            boolean usePolicyMaximum,
            Boolean autoReady) {
        public Plan {
            autoReady = autoReady == null ? Boolean.TRUE : autoReady;
        }

        /** Existing Java callers retain their explicit readiness behaviour. */
        public Plan(
                boolean enabled,
                LocalTime openingTime,
                LocalTime closingTime,
                int slotMinutes,
                int capacity,
                int maintainDays,
                int weekdays,
                long version,
                List<Template> templates,
                boolean usePolicyMaximum) {
            this(
                    enabled,
                    openingTime,
                    closingTime,
                    slotMinutes,
                    capacity,
                    maintainDays,
                    weekdays,
                    version,
                    templates,
                    usePolicyMaximum,
                    false);
        }

        /** Existing callers retain explicit template mode. */
        public Plan(
                boolean enabled,
                LocalTime openingTime,
                LocalTime closingTime,
                int slotMinutes,
                int capacity,
                int maintainDays,
                int weekdays,
                long version,
                List<Template> templates) {
            this(
                    enabled,
                    openingTime,
                    closingTime,
                    slotMinutes,
                    capacity,
                    maintainDays,
                    weekdays,
                    version,
                    templates,
                    false);
        }
    }

    /** Eligible configured policy and its visible quantity limits. */
    public record Policy(
            long id,
            String name,
            String unit,
            int bookingDays,
            BigDecimal buffer,
            BigDecimal maximum,
            boolean readyStockRequired,
            boolean eligible,
            long branchProductId,
            boolean selectable,
            String unavailableReason) {}

    /** Persisted execution diagnostics without claiming skipped items were published. */
    public record Workspace(
            Plan plan,
            List<Policy> policies,
            String lastAttempt,
            String lastSuccess,
            String message,
            RollingAutomationDiagnostics.Health health,
            List<RollingAutomationDiagnostics.Run> runs,
            Integer storefrontBookingDays) {
        /** Internal configuration reads omit expensive admin-only diagnostics. */
        public Workspace(
                Plan plan,
                List<Policy> policies,
                String lastAttempt,
                String lastSuccess,
                String message) {
            this(plan, policies, lastAttempt, lastSuccess, message, null, List.of(), null);
        }
    }

    /** Reads configuration with bounded history and effective coverage for admin. */
    public Workspace inspect(long branchId) {
        final long started = MethodTiming.start(RollingScheduleService.class, "inspect(long)");
        try {
            var workspace = workspace(branchId);
            var runs = diagnostics.runs(branchId);
            return new Workspace(
                    workspace.plan(),
                    workspace.policies(),
                    workspace.lastAttempt(),
                    workspace.lastSuccess(),
                    workspace.message(),
                    diagnostics.health(branchId, workspace, runs),
                    runs,
                    features.getFutureOrderingDays());
        } finally {
            MethodTiming.finish(started, RollingScheduleService.class, "inspect(long)");
        }
    }

    /** Tests a policy maximum without subtracting its safety buffer twice. */
    public static boolean usableMaximum(Policy policy) {
        final long started =
                MethodTiming.start(RollingScheduleService.class, "usableMaximum(Policy)");
        try {
            return policy.eligible()
                    && policy.maximum() != null
                    && policy.maximum().signum() > 0
                    && policy.maximum().compareTo(policy.buffer()) > 0
                    && ("GRAM".equals(policy.unit())
                            || policy.maximum().stripTrailingZeros().scale() <= 0);
        } finally {
            MethodTiming.finish(started, RollingScheduleService.class, "usableMaximum(Policy)");
        }
    }

    /** Reports whether the new scheduler owns inventory generation for this branch. */
    public boolean ownsInventory(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RollingScheduleService.class, "ownsInventory(long)");
        try {
            return Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM rolling_branch_schedules WHERE branch_id=?"
                                    + " AND enabled)",
                            Boolean.class,
                            branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RollingScheduleService.class, "ownsInventory(long)");
        }
    }

    /** Reads this flow's configuration and only existing eligible inventory policies. */
    public Workspace workspace(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RollingScheduleService.class, "workspace(long)");
        try {
            Plan plan =
                    jdbc.query(
                            """
                            SELECT * FROM rolling_branch_schedules WHERE branch_id=?
                            """,
                            rs ->
                                    rs.next()
                                            ? new Plan(
                                                    rs.getBoolean("enabled"),
                                                    rs.getObject("opening_time", LocalTime.class),
                                                    rs.getObject("closing_time", LocalTime.class),
                                                    rs.getInt("slot_minutes"),
                                                    rs.getInt("capacity"),
                                                    rs.getInt("maintain_days"),
                                                    rs.getInt("weekdays"),
                                                    rs.getLong("version"),
                                                    templates(branchId),
                                                    rs.getBoolean("use_policy_maximum"),
                                                    rs.getBoolean("auto_ready"))
                                            : null,
                            branchId);
            var policies =
                    jdbc.query(
                            """
SELECT p.id,pr.name,p.inventory_unit,p.booking_horizon_days,p.default_safety_buffer,
       p.maximum_daily_allocation,p.ready_stock_required,
       (p.online_enabled AND p.control_mode IN ('READY_STOCK','DAILY_PRODUCTION')
         AND bp.available AND pr.active AND c.active AND NOT bp.occasion_only),p.branch_product_id,
       (p.control_mode IN ('READY_STOCK','DAILY_PRODUCTION') AND NOT bp.occasion_only),
       CASE WHEN bp.occasion_only THEN 'Bulk-only items are excluded from daily automation. Remove this saved selection.'
            WHEN p.control_mode='SLOT_CAPACITY' THEN 'Capacity-only policies do not support physical inventory automation. Remove this saved selection if selected.'
            WHEN p.control_mode='MANUAL' THEN 'Manual policies are managed through the manual inventory tools.'
            WHEN NOT p.online_enabled THEN 'Skipped while online inventory is disabled for this policy.'
            WHEN NOT bp.available THEN 'Skipped while this dish is unavailable at the branch.'
            WHEN NOT pr.active THEN 'Skipped while this dish is inactive in the menu.'
            WHEN NOT c.active THEN 'Skipped while this dish’s category is inactive in the menu.' END
FROM branch_inventory_policies p JOIN branch_products bp ON bp.id=p.branch_product_id
JOIN products pr ON pr.id=bp.product_id JOIN categories c ON c.id=pr.category_id
WHERE bp.branch_id=? AND (NOT bp.occasion_only OR EXISTS (
    SELECT 1 FROM rolling_inventory_templates t WHERE t.branch_id=bp.branch_id AND t.policy_id=p.id))
ORDER BY pr.name,p.id
""",
                            (rs, n) ->
                                    new Policy(
                                            rs.getLong(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getInt(4),
                                            rs.getBigDecimal(5),
                                            rs.getBigDecimal(6),
                                            rs.getBoolean(7),
                                            rs.getBoolean(8),
                                            rs.getLong(9),
                                            rs.getBoolean(10),
                                            rs.getString(11)),
                            branchId);
            return jdbc.query(
                    "SELECT last_attempt_at,last_success_at,last_message FROM"
                            + " rolling_branch_schedules WHERE branch_id=?",
                    rs ->
                            rs.next()
                                    ? new Workspace(
                                            plan,
                                            policies,
                                            rs.getString(1),
                                            rs.getString(2),
                                            rs.getString(3))
                                    : new Workspace(null, policies, null, null, null),
                    branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RollingScheduleService.class, "workspace(long)");
        }
    }

    /** Reads separately configured quantities; does not create or modify product policies. */
    private List<Template> templates(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RollingScheduleService.class, "templates(long)");
        try {
            return jdbc.query(
                    "SELECT policy_id,quantity,auto_approve FROM rolling_inventory_templates WHERE"
                            + " branch_id=? ORDER BY policy_id",
                    (rs, n) -> new Template(rs.getLong(1), rs.getBigDecimal(2), rs.getBoolean(3)),
                    branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RollingScheduleService.class, "templates(long)");
        }
    }

    /** Saves a versioned opt-in plan under the same branch lock used by both schedulers. */
    public void save(long branchId, Plan plan, String actor) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RollingScheduleService.class, "save(long,Plan,String)");
        try {
            transactions.executeWithoutResult(
                    tx -> {
                        lock(branchId);
                        if (plan == null)
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST, "A schedule is required.");
                        if (plan.openingTime() == null
                                || plan.closingTime() == null
                                || !plan.closingTime().isAfter(plan.openingTime())
                                || plan.openingTime().getSecond() != 0
                                || plan.openingTime().getNano() != 0
                                || plan.closingTime().getNano() != 0
                                || plan.closingTime().getSecond() != 0
                                || plan.slotMinutes() < 5
                                || plan.slotMinutes() > 180
                                || plan.capacity() < 1
                                || plan.capacity() > 10000
                                || plan.maintainDays() < 1
                                || plan.maintainDays() > 60
                                || plan.weekdays() < 1
                                || plan.weekdays() > 127
                                || java.time.Duration.between(
                                                        plan.openingTime(), plan.closingTime())
                                                .toMinutes()
                                        < plan.slotMinutes()
                                || plan.templates() == null
                                || plan.templates().size() > 1000)
                            throw new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    "Check times, duration, capacity, days and selected policies.");
                        var eligible = workspace(branchId).policies();
                        var previouslySelected =
                                templates(branchId).stream()
                                        .map(Template::policyId)
                                        .collect(java.util.stream.Collectors.toSet());
                        var seen = new java.util.HashSet<Long>();
                        for (var template : plan.templates()) {
                            if (template == null)
                                throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Select valid policy templates.");
                            var policy =
                                    eligible.stream()
                                            .filter(p -> p.id() == template.policyId())
                                            .findFirst()
                                            .orElseThrow(
                                                    () ->
                                                            new ResponseStatusException(
                                                                    HttpStatus.BAD_REQUEST,
                                                                    "Select a configured inventory"
                                                                            + " policy from this"
                                                                            + " branch."));
                            if (!policy.selectable()
                                    && !previouslySelected.contains(template.policyId()))
                                throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Select a supported physical inventory policy. "
                                                + policy.unavailableReason());
                            if (!seen.add(template.policyId())
                                    || template.quantity() == null
                                    || template.quantity().signum() <= 0
                                    || template.quantity().scale() > 3
                                    || template.quantity()
                                                    .compareTo(new BigDecimal("99999999999.999"))
                                            > 0
                                    || plan.enabled()
                                            && !plan.usePolicyMaximum()
                                            && policy.selectable()
                                            && ((!"GRAM".equals(policy.unit())
                                                            && template.quantity()
                                                                            .stripTrailingZeros()
                                                                            .scale()
                                                                    > 0)
                                                    || template.quantity()
                                                                    .compareTo(policy.buffer())
                                                            <= 0
                                                    || policy.maximum() != null
                                                            && template.quantity()
                                                                            .compareTo(
                                                                                    policy
                                                                                            .maximum())
                                                                    > 0))
                                throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Quantity must exceed its buffer and respect policy units"
                                                + " and maximum.");
                        }
                        int changed =
                                jdbc.update(
                                        """
UPDATE rolling_branch_schedules SET enabled=?,opening_time=?,closing_time=?,slot_minutes=?,
capacity=?,maintain_days=?,weekdays=?,use_policy_maximum=?,auto_ready=?,version=version+1,updated_by=?,updated_at=clock_timestamp()
WHERE branch_id=? AND version=?
""",
                                        plan.enabled(),
                                        plan.openingTime(),
                                        plan.closingTime(),
                                        plan.slotMinutes(),
                                        plan.capacity(),
                                        plan.maintainDays(),
                                        plan.weekdays(),
                                        plan.usePolicyMaximum(),
                                        plan.autoReady(),
                                        actor,
                                        branchId,
                                        plan.version());
                        if (changed == 0) {
                            if (plan.version() != -1
                                    || jdbc.update(
                                                    """
INSERT INTO rolling_branch_schedules(branch_id,enabled,opening_time,closing_time,slot_minutes,
capacity,maintain_days,weekdays,use_policy_maximum,auto_ready,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
""",
                                                    branchId,
                                                    plan.enabled(),
                                                    plan.openingTime(),
                                                    plan.closingTime(),
                                                    plan.slotMinutes(),
                                                    plan.capacity(),
                                                    plan.maintainDays(),
                                                    plan.weekdays(),
                                                    plan.usePolicyMaximum(),
                                                    plan.autoReady(),
                                                    actor)
                                            == 0)
                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "The schedule changed. Reload before saving.");
                        }
                        jdbc.update(
                                "DELETE FROM rolling_inventory_templates WHERE branch_id=?",
                                branchId);
                        for (var template : plan.templates())
                            jdbc.update(
                                    "INSERT INTO rolling_inventory_templates VALUES(?,?,?,?)",
                                    branchId,
                                    template.policyId(),
                                    template.quantity(),
                                    template.autoApprove());
                    });
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RollingScheduleService.class,
                    "save(long,Plan,String)");
        }
    }

    /** Locks the branch to coordinate activation, legacy generation and rolling generation. */
    private void lock(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RollingScheduleService.class, "lock(long)");
        try {
            var active =
                    jdbc.query(
                            "SELECT TRUE FROM branches WHERE id=? FOR NO KEY UPDATE",
                            rs -> rs.next() ? rs.getBoolean(1) : null,
                            branchId);
            if (!Boolean.TRUE.equals(active))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Branch not found.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RollingScheduleService.class, "lock(long)");
        }
    }

    /** Reconciles using the system actor for trusted internal callers. */
    public void reconcile(long branchId) {
        final long started = MethodTiming.start(RollingScheduleService.class, "reconcile(long)");
        try {
            reconcile(branchId, "MANUAL", "SYSTEM_ROLLING_AUTOMATION");
        } finally {
            MethodTiming.finish(started, RollingScheduleService.class, "reconcile(long)");
        }
    }

    /**
     * Repairs coverage with a five-minute transaction deadline and durable execution diagnostics.
     */
    public void reconcile(long branchId, String trigger, String actor) {
        final long started =
                MethodTiming.start(RollingScheduleService.class, "reconcile(long,String,String)");
        try {
            reconcile(branchId, trigger, actor, null);
        } finally {
            MethodTiming.finish(
                    started, RollingScheduleService.class, "reconcile(long,String,String)");
        }
    }

    /** Runs only the reviewed saved version when supplied, preventing stale combined actions. */
    public void reconcile(long branchId, String trigger, String actor, Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RollingScheduleService.class, "reconcile(long,String,String,Long)");
        try {
            long began = System.nanoTime();
            long runId = diagnostics.start(branchId, trigger, actor);
            long[] waitMs = {0}, heldSince = {0};
            int[] counts = {0, 0};
            boolean[] completed = {false};
            String[] result = {"No enabled plan; nothing generated."};
            var boundedTransaction = new TransactionTemplate(transactions.getTransactionManager());
            boundedTransaction.setTimeout(300);
            try {
                boundedTransaction.executeWithoutResult(
                        tx -> {
                            long waiting = System.nanoTime();
                            try {
                                lock(branchId);
                                heldSince[0] = System.nanoTime();
                            } finally {
                                waitMs[0] =
                                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                                System.nanoTime() - waiting);
                            }
                            if (!Boolean.TRUE.equals(
                                    jdbc.queryForObject(
                                            "SELECT active AND operational FROM branches WHERE"
                                                    + " id=?",
                                            Boolean.class,
                                            branchId)))
                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Branch must be active and operational.");
                            var config = workspace(branchId);
                            var plan = config.plan();
                            if (expectedVersion != null
                                    && (plan == null || plan.version() != expectedVersion))
                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "The saved setup changed. Reload and review it before"
                                                + " generating stock.");
                            if (plan == null || !plan.enabled()) return;
                            jdbc.update(
                                    "UPDATE rolling_branch_schedules SET last_attempt_at=now()"
                                            + " WHERE branch_id=?",
                                    branchId);
                            LocalDateTime now = LocalDateTime.now(clock.withZone(IST));
                            LocalDate today = now.toLocalDate();
                            int slots = 0, allocations = 0, retained = 0, readinessPublished = 0;
                            int activeDates = 0, noSlotDates = 0, existingAllocations = 0;
                            int unavailable = 0,
                                    outsideWindow = 0,
                                    limited = 0,
                                    readinessUnitMismatches = 0;
                            var policyMap =
                                    config.policies().stream()
                                            .collect(
                                                    java.util.stream.Collectors.toMap(
                                                            Policy::id, policy -> policy));
                            // Maximum is the gross allocation. The existing approval service
                            // withholds the policy buffer once; do not subtract it here.
                            var effectiveTemplates =
                                    plan.usePolicyMaximum()
                                            ? config.policies().stream()
                                                    .filter(RollingScheduleService::usableMaximum)
                                                    .map(
                                                            p ->
                                                                    new Template(
                                                                            p.id(),
                                                                            p.maximum(),
                                                                            true))
                                                    .toList()
                                            : plan.templates();
                            int maximumModeSkips =
                                    plan.usePolicyMaximum()
                                            ? config.policies().size() - effectiveTemplates.size()
                                            : 0;
                            for (int offset = 0; offset < plan.maintainDays(); offset++) {
                                LocalDate date = today.plusDays(offset);
                                if ((plan.weekdays() & (1 << (date.getDayOfWeek().getValue() - 1)))
                                        == 0) continue;
                                jdbc.update(
                                        "INSERT INTO rolling_schedule_dates VALUES(?,?) ON CONFLICT"
                                                + " DO NOTHING",
                                        branchId,
                                        date);
                                // Disabled and manual intervals reserve their time too. Load once
                                // per
                                // date so repeat checks do not attempt an INSERT for every existing
                                // slot.
                                var intervals =
                                        jdbc.query(
                                                "SELECT start_time,end_time FROM pickup_slots WHERE"
                                                        + " branch_id=? AND slot_date=? AND"
                                                        + " occasion_enquiry_id IS NULL",
                                                (rs, row) ->
                                                        new SlotInterval(
                                                                rs.getObject(1, LocalTime.class),
                                                                rs.getObject(2, LocalTime.class)),
                                                branchId,
                                                date);
                                int start = plan.openingTime().toSecondOfDay() / 60,
                                        end = plan.closingTime().toSecondOfDay() / 60;
                                for (int minute = start;
                                        minute + plan.slotMinutes() <= end;
                                        minute += plan.slotMinutes()) {
                                    LocalTime from = LocalTime.of(minute / 60, minute % 60);
                                    LocalTime to =
                                            LocalTime.of(
                                                    (minute + plan.slotMinutes()) / 60,
                                                    (minute + plan.slotMinutes()) % 60);
                                    if (date.equals(today) && !from.isAfter(now.toLocalTime()))
                                        continue;
                                    if (intervals.stream()
                                            .anyMatch(
                                                    interval ->
                                                            interval.start().isBefore(to)
                                                                    && interval.end()
                                                                            .isAfter(from)))
                                        continue;
                                    slots +=
                                            jdbc.update(
                                                    """
INSERT INTO pickup_slots(branch_id,slot_date,start_time,end_time,capacity,booked_count,
priority_enabled,priority_capacity,priority_booked_count,priority_charge,active,rolling_generated)
VALUES(?,?,?,?,?,0,FALSE,0,0,0,TRUE,TRUE) ON CONFLICT DO NOTHING
""",
                                                    branchId,
                                                    date,
                                                    from,
                                                    to,
                                                    plan.capacity());
                                }
                                var first =
                                        jdbc.query(
                                                """
SELECT min(start_time) FROM pickup_slots WHERE branch_id=? AND slot_date=? AND active
AND occasion_enquiry_id IS NULL AND (slot_date<>? OR start_time>?)
""",
                                                rs ->
                                                        rs.next()
                                                                ? rs.getObject(1, LocalTime.class)
                                                                : null,
                                                branchId,
                                                date,
                                                today,
                                                now.toLocalTime());
                                if (first == null) {
                                    noSlotDates++;
                                    continue;
                                }
                                activeDates++;
                                var existing =
                                        new java.util.HashSet<>(
                                                jdbc.queryForList(
                                                        "SELECT a.branch_product_id FROM"
                                                            + " inventory_daily_allocations a JOIN"
                                                            + " branch_products bp ON"
                                                            + " bp.id=a.branch_product_id WHERE"
                                                            + " bp.branch_id=? AND"
                                                            + " a.service_date=?",
                                                        Long.class,
                                                        branchId,
                                                        date));
                                var readinessCandidates =
                                        plan.autoReady()
                                                ? new java.util.HashSet<>(
                                                        jdbc.queryForList(
                                                                "SELECT branch_product_id FROM"
                                                                    + " inventory_daily_allocations"
                                                                    + " WHERE service_date=? AND"
                                                                    + " branch_product_id IN"
                                                                    + " (SELECT id FROM"
                                                                    + " branch_products WHERE"
                                                                    + " branch_id=?) AND"
                                                                    + " status='APPROVED' AND"
                                                                    + " approved_by='SYSTEM_ROLLING_AUTOMATION'"
                                                                    + " AND ready_quantity=0 AND"
                                                                    + " actual_ready_at IS NULL AND"
                                                                    + " held_quantity=0 AND"
                                                                    + " committed_quantity=0 AND"
                                                                    + " wasted_quantity=0",
                                                                Long.class,
                                                                date,
                                                                branchId))
                                                : java.util.Set.<Long>of();
                                for (var template : effectiveTemplates) {
                                    var policy = policyMap.get(template.policyId());
                                    if (policy == null || !policy.eligible()) {
                                        unavailable++;
                                        retained++;
                                        continue;
                                    }
                                    if (existing.contains(policy.branchProductId())) {
                                        if (readinessCandidates.contains(policy.branchProductId())
                                                && template.autoApprove()
                                                && policy.readyStockRequired()
                                                && offset <= policy.bookingDays()
                                                && offset <= features.getFutureOrderingDays()) {
                                            var recovery = publishUntouchedReadiness(policy, date);
                                            if (recovery == ReadinessRecovery.PUBLISHED)
                                                readinessPublished++;
                                            if (recovery == ReadinessRecovery.UNIT_MISMATCH)
                                                readinessUnitMismatches++;
                                        }
                                        existingAllocations++;
                                        retained++;
                                        continue;
                                    }
                                    if (offset > policy.bookingDays()
                                            || offset > features.getFutureOrderingDays()) {
                                        outsideWindow++;
                                        retained++;
                                        continue;
                                    }
                                    int inserted =
                                            jdbc.update(
                                                    """
INSERT INTO inventory_daily_allocations(branch_product_id,service_date,status,inventory_unit,
forecast_quantity,safety_buffer_quantity,expected_ready_at,note,created_at,updated_at)
SELECT p.branch_product_id,?,'DRAFT',p.inventory_unit,?,p.default_safety_buffer,?,
'Created by separate rolling automation',now(),now()
FROM branch_inventory_policies p JOIN branch_products bp ON bp.id=p.branch_product_id
JOIN products pr ON pr.id=bp.product_id JOIN categories c ON c.id=pr.category_id
WHERE pr.active AND c.active AND p.id=? AND bp.branch_id=? AND bp.available AND p.online_enabled AND p.control_mode IN ('READY_STOCK','DAILY_PRODUCTION')
AND NOT bp.occasion_only AND ? > p.default_safety_buffer
AND (p.maximum_daily_allocation IS NULL OR ?<=p.maximum_daily_allocation)
ON CONFLICT(branch_product_id,service_date) DO NOTHING
""",
                                                    date,
                                                    template.quantity(),
                                                    date.atTime(first),
                                                    template.policyId(),
                                                    branchId,
                                                    template.quantity(),
                                                    template.quantity());
                                    if (inserted == 0) {
                                        limited++;
                                        retained++;
                                        continue;
                                    }
                                    if (template.autoApprove()) {
                                        inventory.approveAllocation(
                                                policy.branchProductId(),
                                                date,
                                                new AdminAllocationApprovalRequest(
                                                        template.quantity(),
                                                        null,
                                                        null,
                                                        null,
                                                        date.atTime(first),
                                                        "Guaranteed quantity from separate rolling"
                                                                + " automation"),
                                                "SYSTEM_ROLLING_AUTOMATION");
                                        if (plan.autoReady() && policy.readyStockRequired()) {
                                            publishReadiness(
                                                    policy,
                                                    date,
                                                    template.quantity(),
                                                    date.atTime(first));
                                            readinessPublished++;
                                        }
                                    }
                                    allocations++;
                                }
                            }
                            String message =
                                    slots
                                            + " slots created; "
                                            + allocations
                                            + " allocations created; "
                                            + retained
                                            + " item-dates unchanged. "
                                            + effectiveTemplates.size()
                                            + " selected policies; "
                                            + activeDates
                                            + " dates with upcoming regular pickup slots; "
                                            + noSlotDates
                                            + " selected dates without upcoming regular slots."
                                            + " Skips: "
                                            + existingAllocations
                                            + " existing allocations; "
                                            + unavailable
                                            + " unavailable/offline/unsupported item-dates; "
                                            + outsideWindow
                                            + " outside booking windows; "
                                            + limited
                                            + " policy-limit or concurrent-allocation checks.";
                            message +=
                                    " Automatic readiness: "
                                            + readinessPublished
                                            + " guaranteed allocations marked ready. "
                                            + readinessUnitMismatches
                                            + " allocations skipped for inventory unit mismatch;"
                                            + " review affected dates and dishes in coverage"
                                            + " details.";
                            if (plan.usePolicyMaximum())
                                message +=
                                        " Policy maximum mode: "
                                                + maximumModeSkips
                                                + " policies skipped because they are unavailable,"
                                                + " unsupported or lack a usable maximum above the"
                                                + " buffer.";
                            if (effectiveTemplates.isEmpty())
                                message +=
                                        plan.usePolicyMaximum()
                                                ? " No usable policy maximums. Configure an online"
                                                        + " physical policy with a maximum greater"
                                                        + " than its safety buffer."
                                                : " No dishes selected in the saved plan. Select"
                                                        + " dishes, enter daily quantities and save"
                                                        + " to generate inventory.";
                            if (activeDates == 0)
                                message +=
                                        " No upcoming active regular pickup slots on selected"
                                            + " weekdays in this range. Review Pickup scheduling;"
                                            + " existing disabled or elapsed dates are preserved.";
                            message +=
                                    " Existing allocations are never replenished. Manual-review"
                                        + " drafts and staff readiness overrides are preserved.";
                            jdbc.update(
                                    "UPDATE rolling_branch_schedules SET"
                                        + " last_success_at=clock_timestamp(),last_message=? WHERE"
                                        + " branch_id=?",
                                    message,
                                    branchId);
                            completed[0] = true;
                            counts[0] = slots;
                            counts[1] = allocations;
                            result[0] = message;
                        });
            } catch (RuntimeException failure) {
                String failureSummary =
                        "Generation failed; new branch changes were rolled back. Review setup and"
                                + " retry. Cause: "
                                + (failure instanceof ResponseStatusException response
                                        ? response.getReason()
                                        : failure.getClass().getSimpleName());
                try {
                    jdbc.update(
                            "UPDATE rolling_branch_schedules SET"
                                    + " last_attempt_at=now(),last_message=? WHERE branch_id=?",
                            failureSummary,
                            branchId);
                    diagnostics.finish(
                            runId,
                            branchId,
                            "FAILED",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                    System.nanoTime() - began),
                            waitMs[0],
                            heldSince[0] == 0
                                    ? 0
                                    : java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                            System.nanoTime() - heldSince[0]),
                            0,
                            0,
                            failureSummary);
                } catch (RuntimeException loggingFailure) {
                    failure.addSuppressed(loggingFailure);
                }
                throw failure;
            }
            diagnostics.finish(
                    runId,
                    branchId,
                    completed[0] ? "SUCCESS" : "SKIPPED",
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began),
                    waitMs[0],
                    heldSince[0] == 0
                            ? 0
                            : java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                    System.nanoTime() - heldSince[0]),
                    counts[0],
                    counts[1],
                    result[0]);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RollingScheduleService.class,
                    "reconcile(long,String,String,Long)");
        }
    }

    /**
     * Records the explicitly guaranteed quantity through the existing readiness audit and ledger.
     */
    private void publishReadiness(
            Policy policy, LocalDate date, BigDecimal quantity, LocalDateTime expected) {
        final long started =
                MethodTiming.start(
                        RollingScheduleService.class,
                        "publishReadiness(Policy,LocalDate,BigDecimal,LocalDateTime)");
        try {
            inventory.updateReadiness(
                    policy.branchProductId(),
                    date,
                    new AdminReadinessUpdateRequest(
                            InventoryAllocationStatus.READY,
                            quantity,
                            expected,
                            "Guaranteed ready quantity from separate rolling automation"),
                    "SYSTEM_ROLLING_AUTOMATION");
        } finally {
            MethodTiming.finish(
                    started,
                    RollingScheduleService.class,
                    "publishReadiness(Policy,LocalDate,BigDecimal,LocalDateTime)");
        }
    }

    /**
     * Repairs only a pristine approval made by this flow, never a staff or previously used
     * allocation.
     */
    private ReadinessRecovery publishUntouchedReadiness(Policy policy, LocalDate date) {
        final long started =
                MethodTiming.start(
                        RollingScheduleService.class,
                        "publishUntouchedReadiness(Policy,LocalDate)");
        try {
            // Lock first, then check provenance. A concurrent staff write either wins first and
            // makes this row ineligible, or runs afterward and overrides this guarantee normally.
            var rows =
                    jdbc.queryForList(
                            "SELECT id,approved_quantity,expected_ready_at,inventory_unit FROM"
                                    + " inventory_daily_allocations WHERE branch_product_id=? AND"
                                    + " service_date=? FOR UPDATE",
                            policy.branchProductId(),
                            date);
            if (rows.isEmpty()) return ReadinessRecovery.PRESERVED;
            var row = rows.getFirst();
            if (!policy.unit().equals(row.get("inventory_unit")))
                return ReadinessRecovery.UNIT_MISMATCH;
            long id = ((Number) row.get("id")).longValue();
            Boolean pristine =
                    jdbc.queryForObject(
                            """
SELECT status='APPROVED' AND approved_by='SYSTEM_ROLLING_AUTOMATION'
AND ready_quantity=0 AND actual_ready_at IS NULL AND held_quantity=0
AND committed_quantity=0 AND wasted_quantity=0 AND approved_quantity>safety_buffer_quantity
AND (SELECT count(*) FROM inventory_allocation_plan_audit p WHERE p.allocation_id=a.id)=1
AND EXISTS (SELECT 1 FROM inventory_allocation_plan_audit p WHERE p.allocation_id=a.id AND p.performed_by='SYSTEM_ROLLING_AUTOMATION')
AND NOT EXISTS (SELECT 1 FROM inventory_stock_transactions t WHERE t.allocation_id=a.id AND (t.transaction_type<>'ALLOCATION_APPROVED' OR t.performed_by<>'SYSTEM_ROLLING_AUTOMATION'))
FROM inventory_daily_allocations a WHERE id=?
""",
                            Boolean.class,
                            id);
            if (!Boolean.TRUE.equals(pristine)) return ReadinessRecovery.PRESERVED;
            var expected = (java.sql.Timestamp) row.get("expected_ready_at");
            publishReadiness(
                    policy,
                    date,
                    (BigDecimal) row.get("approved_quantity"),
                    expected == null ? null : expected.toLocalDateTime());
            return ReadinessRecovery.PUBLISHED;
        } finally {
            MethodTiming.finish(
                    started,
                    RollingScheduleService.class,
                    "publishUntouchedReadiness(Policy,LocalDate)");
        }
    }
}
