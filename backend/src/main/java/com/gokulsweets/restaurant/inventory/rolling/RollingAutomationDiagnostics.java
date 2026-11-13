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
    private static final Duration RUN_INACTIVITY_LIMIT = Duration.ofMinutes(10);

    /** One scheduled or staff-triggered execution, including rolled-back failures. */
    public record Run(
            long id,
            String trigger,
            String actor,
            String status,
            String startedAt,
            String lastProgressAt,
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
    private record Allocation(long product, LocalDate date, String status, String unit) {}

    /** Branch-wide execution status, independent of the size of the history response. */
    private record ExecutionHealth(
            Instant enabledSince, Instant lastSuccess, boolean running, String lastOutcome) {}

    /**
     * Claims a run inside a short transaction. The advisory lock serializes admission across
     * instances and the run row keeps ownership after the transaction releases the lock.
     */
    Long tryStart(long branch, String trigger, String actor) {
        final long started =
                MethodTiming.start(
                        RollingAutomationDiagnostics.class, "tryStart(long,String,String)");
        try {
            if (!Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT pg_try_advisory_xact_lock(hashtextextended(?, 0))",
                            Boolean.class,
                            "rolling-automation:" + branch))) return null;
            expireStaleRuns(branch);
            if (Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            "SELECT EXISTS (SELECT 1 FROM rolling_automation_runs"
                                    + " WHERE branch_id=? AND status='RUNNING')",
                            Boolean.class,
                            branch))) return null;
            return insertRun(branch, trigger, actor);
        } finally {
            MethodTiming.finish(
                    started, RollingAutomationDiagnostics.class, "tryStart(long,String,String)");
        }
    }

    /** Starts a durable execution before taking the transactional branch lock. */
    public long start(long branch, String trigger, String actor) {
        final long started =
                MethodTiming.start(RollingAutomationDiagnostics.class, "start(long,String,String)");
        try {
            expireStaleRuns(branch);
            return insertRun(branch, trigger, actor);
        } finally {
            MethodTiming.finish(
                    started, RollingAutomationDiagnostics.class, "start(long,String,String)");
        }
    }

    /** Expires abandoned runs without discarding committed counts. */
    private void expireStaleRuns(long branch) {
        final long started =
                MethodTiming.start(RollingAutomationDiagnostics.class, "expireStaleRuns(long)");
        try {
            jdbc.update(
                    "UPDATE rolling_automation_runs SET"
                        + " status='FAILED',finished_at=?,summary='Execution was interrupted before"
                        + " completion. Review coverage and retry.' WHERE branch_id=? AND"
                        + " status='RUNNING' AND COALESCE(last_progress_at,started_at)<?",
                    java.sql.Timestamp.from(clock.instant()),
                    branch,
                    java.sql.Timestamp.from(clock.instant().minus(RUN_INACTIVITY_LIMIT)));
        } finally {
            MethodTiming.finish(
                    started, RollingAutomationDiagnostics.class, "expireStaleRuns(long)");
        }
    }

    /** Creates the durable run row once admission succeeds. */
    private long insertRun(long branch, String trigger, String actor) {
        final long started =
                MethodTiming.start(
                        RollingAutomationDiagnostics.class, "insertRun(long,String,String)");
        try {
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
                    started, RollingAutomationDiagnostics.class, "insertRun(long,String,String)");
        }
    }

    /** Records a committed date's counts or refreshes activity after a failed date rolls back. */
    public void recordProgress(long id, int slots, int allocations) {
        final long started =
                MethodTiming.start(
                        RollingAutomationDiagnostics.class, "recordProgress(long,int,int)");
        try {
            int updated =
                    jdbc.update(
                            """
                            UPDATE rolling_automation_runs
                            SET last_progress_at=?, slots_created=slots_created+?,
                                allocations_created=allocations_created+?
                            WHERE id=? AND status='RUNNING'
                            """,
                            java.sql.Timestamp.from(clock.instant()),
                            slots,
                            allocations,
                            id);
            if (updated != 1)
                throw new IllegalStateException("Rolling execution is no longer active.");
        } finally {
            MethodTiming.finish(
                    started, RollingAutomationDiagnostics.class, "recordProgress(long,int,int)");
        }
    }

    /** Finishes a run that still owns its claim and retains thirty completed runs per branch. */
    public boolean finish(
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
            if (id < 0) return false;
            int updated =
                    jdbc.update(
                            "UPDATE rolling_automation_runs SET"
                                + " status=?,finished_at=?,duration_ms=?,lock_wait_ms=?,lock_held_ms=?,slots_created=?,allocations_created=?,summary=?"
                                + " WHERE id=? AND branch_id=? AND status='RUNNING'",
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
            if (updated == 0) return false;
            jdbc.update(
                    "DELETE FROM rolling_automation_runs WHERE branch_id=? AND status<>'RUNNING'"
                        + " AND id NOT IN (SELECT id FROM rolling_automation_runs WHERE branch_id=?"
                        + " AND status<>'RUNNING' ORDER BY finished_at DESC,id DESC LIMIT 30)",
                    branch,
                    branch);
            return true;
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
                    "SELECT * FROM rolling_automation_runs WHERE branch_id=?"
                            + " ORDER BY COALESCE(finished_at,started_at) DESC,id DESC LIMIT 30",
                    (rs, n) ->
                            new Run(
                                    rs.getLong("id"),
                                    rs.getString("trigger_type"),
                                    rs.getString("actor"),
                                    rs.getString("status"),
                                    rs.getTimestamp("started_at").toInstant().toString(),
                                    rs.getTimestamp("last_progress_at") == null
                                            ? null
                                            : rs.getTimestamp("last_progress_at")
                                                    .toInstant()
                                                    .toString(),
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
                            "SELECT a.branch_product_id,a.service_date,a.status,a.inventory_unit"
                                + " FROM inventory_daily_allocations a JOIN branch_products bp ON"
                                + " bp.id=a.branch_product_id WHERE bp.branch_id=? AND"
                                + " a.service_date BETWEEN ? AND ?",
                            (rs, n) ->
                                    new Allocation(
                                            rs.getLong(1),
                                            rs.getObject(2, LocalDate.class),
                                            rs.getString(3),
                                            rs.getString(4)),
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
                    readiness = 0,
                    unitMismatches = 0;
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
                        if (!policy.unit().equals(allocation.unit())) {
                            unitMismatches++;
                            if (itemIssues.size() < 15)
                                itemIssues.add(
                                        new Issue(
                                                date,
                                                policy.name(),
                                                "Stored inventory unit "
                                                        + allocation.unit()
                                                        + " differs from policy unit "
                                                        + policy.unit()
                                                        + ". Existing stock was preserved; review"
                                                        + " this allocation manually."));
                        }
                        if (allocation.status().equals("DRAFT")) drafts++;
                        if (policy.readyStockRequired() && allocation.status().equals("APPROVED"))
                            readiness++;
                    }
                }
            }
            var midnight = today.atStartOfDay(IST).toInstant();
            var staleBefore = now.toInstant().minus(RUN_INACTIVITY_LIMIT);
            var execution =
                    jdbc.query(
                            """
                            SELECT s.enabled_since_at,
                                GREATEST(s.last_success_at, (
                                    SELECT max(r.finished_at) FROM rolling_automation_runs r
                                    WHERE r.branch_id=s.branch_id AND r.status='SUCCESS'
                                )),
                                EXISTS (
                                    SELECT 1 FROM rolling_automation_runs r
                                    WHERE r.branch_id=s.branch_id AND r.status='RUNNING'
                                      AND COALESCE(r.last_progress_at, r.started_at)>=?
                                ), (
                                    SELECT r.status FROM rolling_automation_runs r
                                    WHERE r.branch_id=s.branch_id
                                      AND r.status IN ('SUCCESS', 'FAILED')
                                    ORDER BY r.finished_at DESC, r.id DESC LIMIT 1
                                )
                            FROM rolling_branch_schedules s WHERE s.branch_id=?
                            """,
                            rs -> {
                                if (!rs.next())
                                    return new ExecutionHealth(now.toInstant(), null, false, null);
                                var success = rs.getTimestamp(2);
                                return new ExecutionHealth(
                                        rs.getTimestamp(1).toInstant(),
                                        success == null ? null : success.toInstant(),
                                        rs.getBoolean(3),
                                        rs.getString(4));
                            },
                            java.sql.Timestamp.from(staleBefore),
                            branch);
            boolean running = execution.running();
            boolean overdue =
                    !running
                            && !now.toInstant().isBefore(midnight.plusSeconds(900))
                            && execution.enabledSince().isBefore(midnight)
                            && (execution.lastSuccess() == null
                                    || execution.lastSuccess().isBefore(midnight));
            var latest = runs.isEmpty() ? null : runs.getFirst();
            boolean interrupted =
                    !running
                            && latest != null
                            && latest.status().equals("RUNNING")
                            && Instant.parse(
                                            Objects.requireNonNullElse(
                                                    latest.lastProgressAt(), latest.startedAt()))
                                    .isBefore(staleBefore);
            boolean failed = !running && "FAILED".equals(execution.lastOutcome());
            boolean attention =
                    overdue
                            || failed
                            || interrupted
                            || missingSlots > 0
                            || missingAllocations > 0
                            || unitMismatches > 0
                            || included.isEmpty();
            String status = running ? "RUNNING" : attention ? "NEEDS_ATTENTION" : "COMPLETE";
            String message =
                    running
                            ? "A coverage run is in progress. Reload after it completes."
                            : unitMismatches > 0
                                    ? unitMismatches
                                            + " allocations have inventory unit mismatches. Review"
                                            + " affected dates and dishes below; existing units and"
                                            + " quantities were preserved."
                                    : attention
                                            ? "Review missing coverage or execution alerts. Save"
                                                  + " configuration changes before a manual retry."
                                            : "Expected setup records are present. Stock, approval,"
                                                    + " readiness and service-time checks still"
                                                    + " apply.";
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
