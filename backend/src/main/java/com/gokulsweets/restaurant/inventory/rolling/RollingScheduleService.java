package com.gokulsweets.restaurant.inventory.rolling;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.inventory.dto.AdminAllocationApprovalRequest;
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

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

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
            List<Template> templates) {}

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
            long branchProductId) {}

    /** Persisted execution diagnostics without claiming skipped items were published. */
    public record Workspace(
            Plan plan,
            List<Policy> policies,
            String lastAttempt,
            String lastSuccess,
            String message) {}

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
                                                    templates(branchId))
                                            : null,
                            branchId);
            var policies =
                    jdbc.query(
                            """
SELECT p.id,pr.name,p.inventory_unit,p.booking_horizon_days,p.default_safety_buffer,
       p.maximum_daily_allocation,p.ready_stock_required,
       (p.online_enabled AND p.control_mode<>'MANUAL' AND bp.available),p.branch_product_id
FROM branch_inventory_policies p JOIN branch_products bp ON bp.id=p.branch_product_id
JOIN products pr ON pr.id=bp.product_id
WHERE bp.branch_id=? AND NOT bp.occasion_only
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
                                            rs.getLong(9)),
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
                            if (!seen.add(template.policyId())
                                    || template.quantity() == null
                                    || template.quantity().signum() <= 0
                                    || template.quantity().scale() > 3
                                    || template.quantity()
                                                    .compareTo(new BigDecimal("99999999999.999"))
                                            > 0
                                    || (!"GRAM".equals(policy.unit())
                                            && template.quantity().stripTrailingZeros().scale() > 0)
                                    || plan.enabled()
                                            && (template.quantity().compareTo(policy.buffer()) <= 0
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
capacity=?,maintain_days=?,weekdays=?,version=version+1,updated_by=?,updated_at=now()
WHERE branch_id=? AND version=?
""",
                                        plan.enabled(),
                                        plan.openingTime(),
                                        plan.closingTime(),
                                        plan.slotMinutes(),
                                        plan.capacity(),
                                        plan.maintainDays(),
                                        plan.weekdays(),
                                        actor,
                                        branchId,
                                        plan.version());
                        if (changed == 0) {
                            if (plan.version() != -1
                                    || jdbc.update(
                                                    """
INSERT INTO rolling_branch_schedules(branch_id,enabled,opening_time,closing_time,slot_minutes,
capacity,maintain_days,weekdays,updated_by) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
""",
                                                    branchId,
                                                    plan.enabled(),
                                                    plan.openingTime(),
                                                    plan.closingTime(),
                                                    plan.slotMinutes(),
                                                    plan.capacity(),
                                                    plan.maintainDays(),
                                                    plan.weekdays(),
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
                            "SELECT TRUE FROM branches WHERE id=? FOR UPDATE",
                            rs -> rs.next() ? rs.getBoolean(1) : null,
                            branchId);
            if (!Boolean.TRUE.equals(active))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Branch not found.");
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RollingScheduleService.class, "lock(long)");
        }
    }

    /** Fills missing future dates once; existing manual dates and allocations are retained. */
    public void reconcile(long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RollingScheduleService.class, "reconcile(long)");
        try {
            try {
                transactions.executeWithoutResult(
                        tx -> {
                            lock(branchId);
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
                            if (plan == null || !plan.enabled()) return;
                            jdbc.update(
                                    "UPDATE rolling_branch_schedules SET last_attempt_at=now()"
                                            + " WHERE branch_id=?",
                                    branchId);
                            LocalDateTime now = LocalDateTime.now(clock.withZone(IST));
                            LocalDate today = now.toLocalDate();
                            int slots = 0, allocations = 0, retained = 0;
                            var policyMap =
                                    config.policies().stream()
                                            .collect(
                                                    java.util.stream.Collectors.toMap(
                                                            Policy::id, policy -> policy));
                            for (int offset = 0; offset < plan.maintainDays(); offset++) {
                                LocalDate date = today.plusDays(offset);
                                if ((plan.weekdays() & (1 << (date.getDayOfWeek().getValue() - 1)))
                                        == 0) continue;
                                boolean dateClaimed =
                                        jdbc.update(
                                                        "INSERT INTO rolling_schedule_dates"
                                                            + " VALUES(?,?) ON CONFLICT DO NOTHING",
                                                        branchId,
                                                        date)
                                                > 0;
                                if (dateClaimed
                                        && !Boolean.TRUE.equals(
                                                jdbc.queryForObject(
                                                        "SELECT EXISTS(SELECT 1 FROM pickup_slots"
                                                            + " WHERE branch_id=? AND slot_date=?"
                                                            + " AND occasion_enquiry_id IS NULL)",
                                                        Boolean.class,
                                                        branchId,
                                                        date))) {
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
                                if (first == null) continue;
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
                                for (var template : plan.templates()) {
                                    var policy = policyMap.get(template.policyId());
                                    if (policy == null
                                            || !policy.eligible()
                                            || existing.contains(policy.branchProductId())
                                            || offset > policy.bookingDays()
                                            || features.isInventoryAutomationV2()
                                                    && offset > features.getFutureOrderingDays()) {
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
WHERE p.id=? AND bp.branch_id=? AND bp.available AND p.online_enabled AND p.control_mode<>'MANUAL'
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
                                        retained++;
                                        continue;
                                    }
                                    if (template.autoApprove()) {
                                        inventory.approveAllocation(
                                                policy.branchProductId(),
                                                date,
                                                new AdminAllocationApprovalRequest(
                                                        template.quantity(),
                                                        policy.buffer(),
                                                        null,
                                                        null,
                                                        date.atTime(first),
                                                        "Guaranteed quantity from separate rolling"
                                                                + " automation"),
                                                "SYSTEM_ROLLING_AUTOMATION");
                                    }
                                    allocations++;
                                }
                            }
                            jdbc.update(
                                    """
UPDATE rolling_branch_schedules SET last_success_at=now(),last_message=? WHERE branch_id=?
""",
                                    slots
                                            + " slots created; "
                                            + allocations
                                            + " allocations created; "
                                            + retained
                                            + " existing or ineligible item-dates left unchanged."
                                            + " Drafts and ready stock may need staff action.",
                                    branchId);
                        });
            } catch (RuntimeException failure) {
                jdbc.update(
                        "UPDATE rolling_branch_schedules SET last_attempt_at=now(),last_message=?"
                                + " WHERE branch_id=?",
                        "Generation failed. Existing records were retained; retry after checking"
                                + " policies and branch status.",
                        branchId);
                throw failure;
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RollingScheduleService.class, "reconcile(long)");
        }
    }
}
