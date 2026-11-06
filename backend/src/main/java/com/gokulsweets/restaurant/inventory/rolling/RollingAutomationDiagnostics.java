package com.gokulsweets.restaurant.inventory.rolling;

import com.gokulsweets.restaurant.config.EnhancementProperties;
import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/** Bounded admin coverage diagnostics and durable execution history; never generates stock. */
@Service
@RequiredArgsConstructor
public class RollingAutomationDiagnostics {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final EnhancementProperties features;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** One scheduled or staff-triggered execution, including rolled-back failures. */
    public record Run(
            long id,
            String trigger,
            String actor,
            String status,
            String startedAt,
            String finishedAt,
            long durationMs,
            long lockWaitMs,
            long lockHeldMs,
            int slotsCreated,
            int allocationsCreated,
            String summary) {}

    /** One bounded example of a missing setup record. */
    public record Issue(LocalDate date, String item, String reason) {}

    /** Setup coverage and operational alerts, separate from customer sellability. */
    public record Health(
            String status,
            boolean overdue,
            boolean lastRunFailed,
            boolean interrupted,
            int missingSlots,
            int missingAllocations,
            int expectedAllocations,
            int closedDates,
            int includedPolicies,
            int excludedPolicies,
            int drafts,
            int readinessPending,
            List<Issue> issues,
            String message) {}

    /** Regular slots, including disabled intervals that reserve their time intentionally. */
    private record Slot(LocalDate date, LocalTime start, LocalTime end, boolean active) {}

    /** Existing allocation state; quantities and operational overrides remain authoritative. */
    private record Allocation(long product, LocalDate date, String status) {}

    /** Starts a durable execution before taking the transactional branch lock. */
    public long start(long branch, String trigger, String actor) {
        final long started =
                MethodTiming.start(RollingAutomationDiagnostics.class, "start(long,String,String)");
        try {
            jdbc.update(
                    "UPDATE rolling_automation_runs SET"
                        + " status='FAILED',finished_at=?,summary='Execution was interrupted before"
                        + " completion. Review coverage and retry.' WHERE branch_id=? AND"
                        + " status='RUNNING' AND started_at<?",
                    java.sql.Timestamp.from(clock.instant()),
                    branch,
                    java.sql.Timestamp.from(clock.instant().minusSeconds(3600)));
            return jdbc.query(
                    "INSERT INTO"
                        + " rolling_automation_runs(branch_id,trigger_type,actor,status,started_at)"
                        + " SELECT id,?,?,'RUNNING',? FROM branches WHERE id=? RETURNING id",
                    rs -> rs.next() ? rs.getLong(1) : -1L,
                    trigger,
                    actor,
                    java.sql.Timestamp.from(clock.instant()),
                    branch);
        } finally {
            MethodTiming.finish(
                    started, RollingAutomationDiagnostics.class, "start(long,String,String)");
        }
    }

    /** Finishes and retains the latest thirty completed runs per branch. */
    public void finish(
            long id,
            long branch,
            String status,
            long durationMs,
            long lockWaitMs,
            long lockHeldMs,
            int slots,
            int allocations,
            String summary) {
        final long started =
                MethodTiming.start(
                        RollingAutomationDiagnostics.class,
                        "finish(long,long,String,long,long,long,int,int,String)");
        try {
            if (id < 0) return;
            jdbc.update(
                    "UPDATE rolling_automation_runs SET"
                        + " status=?,finished_at=?,duration_ms=?,lock_wait_ms=?,lock_held_ms=?,slots_created=?,allocations_created=?,summary=?"
                        + " WHERE id=? AND branch_id=?",
                    status,
                    java.sql.Timestamp.from(clock.instant()),
                    durationMs,
                    lockWaitMs,
                    lockHeldMs,
                    slots,
                    allocations,
                    summary,
                    id,
                    branch);
            jdbc.update(
                    "DELETE FROM rolling_automation_runs WHERE branch_id=? AND status<>'RUNNING'"
                        + " AND id NOT IN (SELECT id FROM rolling_automation_runs WHERE branch_id=?"
                        + " AND status<>'RUNNING' ORDER BY started_at DESC,id DESC LIMIT 30)",
                    branch,
                    branch);
        } finally {
            MethodTiming.finish(
                    started,
                    RollingAutomationDiagnostics.class,
                    "finish(long,long,String,long,long,long,int,int,String)");
        }
    }

    /** Returns at most thirty recent executions for the already-authorized branch. */
    public List<Run> runs(long branch) {
        final long started = MethodTiming.start(RollingAutomationDiagnostics.class, "runs(long)");
        try {
            return jdbc.query(
                    "SELECT * FROM rolling_automation_runs WHERE branch_id=? ORDER BY started_at"
                            + " DESC,id DESC LIMIT 30",
                    (rs, n) ->
                            new Run(
                                    rs.getLong("id"),
                                    rs.getString("trigger_type"),
                                    rs.getString("actor"),
                                    rs.getString("status"),
                                    rs.getTimestamp("started_at").toInstant().toString(),
                                    rs.getTimestamp("finished_at") == null
                                            ? null
                                            : rs.getTimestamp("finished_at").toInstant().toString(),
                                    rs.getLong("duration_ms"),
                                    rs.getLong("lock_wait_ms"),
                                    rs.getLong("lock_held_ms"),
                                    rs.getInt("slots_created"),
                                    rs.getInt("allocations_created"),
                                    rs.getString("summary")),
                    branch);
        } finally {
            MethodTiming.finish(started, RollingAutomationDiagnostics.class, "runs(long)");
        }
    }

    /**
     * Audits effective coverage with bulk reads; disabled intervals and booking limits are
     * respected.
     */
    public Health health(long branch, RollingScheduleService.Workspace workspace, List<Run> runs) {
        final long started =
                MethodTiming.start(
                        RollingAutomationDiagnostics.class,
                        "health(long,RollingScheduleService.Workspace,List<Run>)");
        try {
            var plan = workspace.plan();
            if (plan == null || !plan.enabled())
                return new Health(
                        "PAUSED",
                        false,
                        false,
                        false,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        List.of(),
                        "Enable and save a plan to monitor coverage.");
            var now = ZonedDateTime.now(clock.withZone(IST));
            var today = now.toLocalDate();
            var lastDate = today.plusDays(plan.maintainDays() - 1L);
            var policies = new HashMap<Long, RollingScheduleService.Policy>();
            workspace.policies().forEach(policy -> policies.put(policy.id(), policy));
            var included =
                    plan.usePolicyMaximum()
                            ? workspace.policies().stream()
                                    .filter(RollingScheduleService::usableMaximum)
                                    .toList()
                            : plan.templates().stream()
                                    .map(t -> policies.get(t.policyId()))
                                    .filter(Objects::nonNull)
                                    .filter(RollingScheduleService.Policy::eligible)
                                    .toList();
            var slots =
                    jdbc.query(
                            "SELECT slot_date,start_time,end_time,active FROM pickup_slots WHERE"
                                    + " branch_id=? AND slot_date BETWEEN ? AND ? AND"
                                    + " occasion_enquiry_id IS NULL",
                            (rs, n) ->
                                    new Slot(
                                            rs.getObject(1, LocalDate.class),
                                            rs.getObject(2, LocalTime.class),
                                            rs.getObject(3, LocalTime.class),
                                            rs.getBoolean(4)),
                            branch,
                            today,
                            lastDate);
            var byDate = new HashMap<LocalDate, List<Slot>>();
            slots.forEach(
                    slot ->
                            byDate.computeIfAbsent(slot.date(), ignored -> new ArrayList<>())
                                    .add(slot));
            var allocations =
                    jdbc.query(
                            "SELECT a.branch_product_id,a.service_date,a.status FROM"
                                    + " inventory_daily_allocations a JOIN branch_products bp ON"
                                    + " bp.id=a.branch_product_id WHERE bp.branch_id=? AND"
                                    + " a.service_date BETWEEN ? AND ?",
                            (rs, n) ->
                                    new Allocation(
                                            rs.getLong(1),
                                            rs.getObject(2, LocalDate.class),
                                            rs.getString(3)),
                            branch,
                            today,
                            lastDate);
            var byItemDate = new HashMap<String, Allocation>();
            allocations.forEach(a -> byItemDate.put(a.product() + ":" + a.date(), a));
            int missingSlots = 0,
                    missingAllocations = 0,
                    expected = 0,
                    closed = 0,
                    drafts = 0,
                    readiness = 0;
            var slotIssues = new ArrayList<Issue>();
            var itemIssues = new ArrayList<Issue>();
            for (int offset = 0; offset < plan.maintainDays(); offset++) {
                var date = today.plusDays(offset);
                if ((plan.weekdays() & (1 << (date.getDayOfWeek().getValue() - 1))) == 0) continue;
                var intervals = byDate.getOrDefault(date, List.of());
                int dateMissing = 0;
                for (int minute = plan.openingTime().toSecondOfDay() / 60;
                        minute + plan.slotMinutes() <= plan.closingTime().toSecondOfDay() / 60;
                        minute += plan.slotMinutes()) {
                    var from = LocalTime.of(minute / 60, minute % 60);
                    var to = from.plusMinutes(plan.slotMinutes());
                    if (date.equals(today) && !from.isAfter(now.toLocalTime())) continue;
                    if (intervals.stream()
                            .anyMatch(
                                    slot -> slot.start().isBefore(to) && slot.end().isAfter(from)))
                        continue;
                    missingSlots++;
                    dateMissing++;
                    if (slotIssues.size() < 15)
                        slotIssues.add(
                                new Issue(
                                        date,
                                        "Pickup slots",
                                        "Missing interval " + from + "–" + to));
                }
                boolean active =
                        intervals.stream()
                                .anyMatch(
                                        slot ->
                                                slot.active()
                                                        && (!date.equals(today)
                                                                || slot.start()
                                                                        .isAfter(
                                                                                now
                                                                                        .toLocalTime())));
                if (!active && dateMissing == 0) {
                    closed++;
                    continue;
                }
                for (var policy : included) {
                    if (offset > policy.bookingDays() || offset > features.getFutureOrderingDays())
                        continue;
                    expected++;
                    var allocation = byItemDate.get(policy.branchProductId() + ":" + date);
                    if (allocation == null) {
                        missingAllocations++;
                        if (itemIssues.size() < 15)
                            itemIssues.add(
                                    new Issue(
                                            date,
                                            policy.name(),
                                            active
                                                    ? "Daily allocation is missing."
                                                    : "Allocation awaits a missing regular pickup"
                                                            + " slot."));
                    } else {
                        if (allocation.status().equals("DRAFT")) drafts++;
                        if (policy.readyStockRequired() && allocation.status().equals("APPROVED"))
                            readiness++;
                    }
                }
            }
            var midnight = today.atStartOfDay(IST).toInstant();
            var timestamps =
                    jdbc.query(
                            "SELECT s.enabled_since_at,GREATEST(s.last_success_at,"
                                    + " (SELECT max(r.finished_at) FROM rolling_automation_runs r"
                                    + " WHERE r.branch_id=s.branch_id AND r.status='SUCCESS'))"
                                    + " FROM rolling_branch_schedules s WHERE s.branch_id=?",
                            rs -> {
                                if (!rs.next()) return new Instant[] {clock.instant(), null};
                                var success = rs.getTimestamp(2);
                                return new Instant[] {
                                    rs.getTimestamp(1).toInstant(),
                                    success == null ? null : success.toInstant()
                                };
                            },
                            branch);
            boolean overdue =
                    !now.toInstant().isBefore(midnight.plusSeconds(900))
                            && timestamps[0].isBefore(midnight)
                            && (timestamps[1] == null || timestamps[1].isBefore(midnight));
            var latest = runs.isEmpty() ? null : runs.getFirst();
            boolean interrupted =
                    latest != null
                            && latest.status().equals("RUNNING")
                            && Instant.parse(latest.startedAt())
                                    .isBefore(clock.instant().minusSeconds(600));
            boolean failed = latest != null && latest.status().equals("FAILED");
            boolean running = latest != null && latest.status().equals("RUNNING") && !interrupted;
            boolean attention =
                    overdue
                            || failed
                            || interrupted
                            || missingSlots > 0
                            || missingAllocations > 0
                            || included.isEmpty();
            String status = running ? "RUNNING" : attention ? "NEEDS_ATTENTION" : "COMPLETE";
            String message =
                    running
                            ? "A coverage run is in progress. Reload after it completes."
                            : attention
                                    ? "Review missing coverage or execution alerts. Save"
                                            + " configuration changes before a manual retry."
                                    : "Expected setup records are present. Stock, approval,"
                                            + " readiness and service-time checks still apply.";
            return new Health(
                    status,
                    overdue,
                    failed,
                    interrupted,
                    missingSlots,
                    missingAllocations,
                    expected,
                    closed,
                    included.size(),
                    workspace.policies().size() - included.size(),
                    drafts,
                    readiness,
                    java.util.stream.Stream.concat(slotIssues.stream(), itemIssues.stream())
                            .toList(),
                    message);
        } finally {
            MethodTiming.finish(
                    started,
                    RollingAutomationDiagnostics.class,
                    "health(long,RollingScheduleService.Workspace,List<Run>)");
        }
    }
}
