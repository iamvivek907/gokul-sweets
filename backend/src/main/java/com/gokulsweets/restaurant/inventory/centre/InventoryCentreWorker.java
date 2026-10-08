package com.gokulsweets.restaurant.inventory.centre;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffUserDetailsService;
import com.gokulsweets.restaurant.staff.StaffUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** Backend inventory centre worker contract and implementation. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "inventory.centre.worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InventoryCentreWorker {

    private final JdbcTemplate jdbc;

    private final PlatformTransactionManager manager;

    private final InventoryCentreProcessor processor;

    private final StaffUserRepository staff;

    private final StaffUserDetailsService users;

    private final ObjectMapper mapper;

    /** Immutable claim data contract. */
    record Claim(long task, UUID job, long branch, long actor, UUID token) {}

    /** Runs batch. */
    @Scheduled(
            fixedDelayString = "${inventory.centre.poll-ms:2000}",
            initialDelayString = "${inventory.centre.poll-ms:2000}")
    public void runBatch() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreWorker.class, "runBatch()");
        try {
            long start = System.nanoTime();
            int processed = 0;
            var transaction = new TransactionTemplate(manager);
            for (int index = 0; index < 5; index++) {
                Claim claim =
                        transaction.execute(
                                tx -> {
                                    var rows =
                                            jdbc.queryForList(
                                                    "SELECT"
                                                        + " t.id,t.job_id,t.attempts,j.branch_id,j.staff_id"
                                                        + " FROM inventory_centre_tasks t JOIN"
                                                        + " inventory_centre_jobs j ON"
                                                        + " j.id=t.job_id WHERE t.status='QUEUED'"
                                                        + " OR (t.status='PROCESSING' AND"
                                                        + " t.lease_until<now()) ORDER BY t.id FOR"
                                                        + " UPDATE OF t,j SKIP LOCKED LIMIT 1");
                                    if (rows.isEmpty()) return null;
                                    var r = rows.getFirst();
                                    long task = ((Number) r.get("id")).longValue();
                                    UUID job = (UUID) r.get("job_id");
                                    if (((Number) r.get("attempts")).intValue() >= 3) {
                                        jdbc.update(
                                                "UPDATE inventory_centre_tasks SET"
                                                        + " status='FAILED',error='Worker stopped"
                                                        + " repeatedly. Review and submit a fresh"
                                                        + " plan.',finished_at=now() WHERE id=?",
                                                task);
                                        jdbc.update(
                                                "UPDATE inventory_centre_jobs SET"
                                                        + " failed=failed+1,updated_at=now() WHERE"
                                                        + " id=?",
                                                job);
                                        return null;
                                    }
                                    UUID token = UUID.randomUUID();
                                    jdbc.update(
                                            "UPDATE inventory_centre_tasks SET"
                                                + " status='PROCESSING',claim_token=?,lease_until=now()+INTERVAL"
                                                + " '10 minutes',attempts=attempts+1 WHERE id=?",
                                            token,
                                            task);
                                    return new Claim(
                                            task,
                                            job,
                                            ((Number) r.get("branch_id")).longValue(),
                                            ((Number) r.get("staff_id")).longValue(),
                                            token);
                                });
                if (claim == null) break;
                processed++;
                var previous = SecurityContextHolder.getContext();
                try {
                    transaction.executeWithoutResult(
                            tx -> {
                                var rows =
                                        jdbc.queryForList(
                                                "SELECT payload FROM inventory_centre_tasks WHERE"
                                                        + " id=? AND status='PROCESSING' AND"
                                                        + " claim_token=? FOR UPDATE",
                                                claim.task(),
                                                claim.token());
                                if (rows.isEmpty()) return;
                                var owner = staff.findDetailedById(claim.actor()).orElseThrow();
                                var details = users.loadUserByUsername(owner.getUsername());
                                if (details.getAuthorities().stream()
                                                .noneMatch(
                                                        a -> a.getAuthority().equals("MENU_MANAGE"))
                                        || details.getAuthorities().stream()
                                                .noneMatch(
                                                        a ->
                                                                a.getAuthority()
                                                                        .equals("INVENTORY_MANAGE"))
                                        || !owner.getRole().getName().equals("OWNER_ADMIN")
                                                && owner.getBranches().stream()
                                                        .noneMatch(
                                                                b -> b.getId() == claim.branch()))
                                    throw new org.springframework.security.access
                                            .AccessDeniedException(
                                            "Requester no longer has branch inventory access.");
                                if (!details.isEnabled())
                                    throw new org.springframework.security.access
                                            .AccessDeniedException("Requester disabled.");
                                var context = SecurityContextHolder.createEmptyContext();
                                context.setAuthentication(
                                        UsernamePasswordAuthenticationToken.authenticated(
                                                details, null, details.getAuthorities()));
                                SecurityContextHolder.setContext(context);
                                processor.apply(
                                        claim.branch(),
                                        mapper.readValue(
                                                (String) rows.getFirst().get("payload"),
                                                InventoryCentreJobs.Work.class));
                                jdbc.update(
                                        "UPDATE inventory_centre_tasks SET"
                                            + " status='SUCCEEDED',payload=NULL,lease_until=NULL,finished_at=now()"
                                            + " WHERE id=?",
                                        claim.task());
                                jdbc.update(
                                        "UPDATE inventory_centre_jobs SET"
                                            + " succeeded=succeeded+1,updated_at=now() WHERE id=?",
                                        claim.job());
                            });
                    log.info(
                            "Inventory item committed: jobId={}, taskId={}, branchId={}",
                            claim.job(),
                            claim.task(),
                            claim.branch());
                } catch (Exception failure) {
                    transaction.executeWithoutResult(
                            tx -> {
                                int changed =
                                        jdbc.update(
                                                "UPDATE inventory_centre_tasks SET"
                                                    + " status='FAILED',error=?,lease_until=NULL,finished_at=now()"
                                                    + " WHERE id=? AND claim_token=? AND"
                                                    + " status='PROCESSING'",
                                                safeMessage(failure),
                                                claim.task(),
                                                claim.token());
                                if (changed > 0)
                                    jdbc.update(
                                            "UPDATE inventory_centre_jobs SET"
                                                + " failed=failed+1,updated_at=now() WHERE id=?",
                                            claim.job());
                            });
                    log.warn(
                            "Inventory item rolled back: jobId={}, taskId={}, branchId={}, type={}",
                            claim.job(),
                            claim.task(),
                            claim.branch(),
                            failure.getClass().getSimpleName(),
                            failure);
                } finally {
                    SecurityContextHolder.setContext(previous);
                }
            }
            if (processed > 0)
                log.info(
                        "Inventory backend batch completed: items={}, elapsedMs={}",
                        processed,
                        (System.nanoTime() - start) / 1000000);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryCentreWorker.class, "runBatch()");
        }
    }

    /**
     * Safes message.
     *
     * @param failure the failure
     * @return the safe message result
     */
    static String safeMessage(Exception failure) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreWorker.class, "safeMessage(Exception)");
        try {
            if ((failure instanceof IllegalArgumentException
                            || failure
                                    instanceof
                                    com.gokulsweets.restaurant.inventory.exception
                                            .InventoryConflictException)
                    && failure.getMessage() != null)
                return failure.getMessage()
                        .substring(0, Math.min(500, failure.getMessage().length()));
            return "Item failed. Reload its current stock, permissions and limits before submitting"
                    + " a fresh plan.";
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    InventoryCentreWorker.class,
                    "safeMessage(Exception)");
        }
    }

    /** Cleanups the operation. */
    @Scheduled(fixedDelayString = "${inventory.centre.cleanup-ms:3600000}")
    public void cleanup() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryCentreWorker.class, "cleanup()");
        try {
            // Only obsolete queue work is removed. Permanent job summaries, stock ledger,
            // allocations, orders and audits are untouched.
            int count =
                    jdbc.update(
                            "DELETE FROM inventory_centre_tasks WHERE id IN (SELECT t.id FROM"
                                    + " inventory_centre_tasks t JOIN inventory_centre_jobs j ON"
                                    + " j.id=t.job_id WHERE t.status='SUCCEEDED' AND"
                                    + " t.finished_at<now()-INTERVAL '30 days' AND"
                                    + " j.succeeded+j.failed=j.total ORDER BY t.finished_at LIMIT"
                                    + " 1000)");
            int payloads =
                    jdbc.update(
                            "UPDATE inventory_centre_tasks SET payload=NULL WHERE id IN (SELECT id"
                                + " FROM inventory_centre_tasks WHERE status='FAILED' AND payload"
                                + " IS NOT NULL AND finished_at<now()-INTERVAL '90 days' ORDER BY"
                                + " id LIMIT 1000)");
            if (count + payloads > 0)
                log.info(
                        "Inventory queue cleanup: successfulTasksDeleted={},"
                                + " expiredFailurePayloadsCleared={}",
                        count,
                        payloads);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryCentreWorker.class, "cleanup()");
        }
    }
}
