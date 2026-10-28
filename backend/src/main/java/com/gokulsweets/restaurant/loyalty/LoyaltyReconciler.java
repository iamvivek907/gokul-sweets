package com.gokulsweets.restaurant.loyalty;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded durable reconciliation, plus slower repair and account-expiry maintenance. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyReconciler {
    @org.springframework.beans.factory.annotation.Value("${gokul.jobs.worker-enabled:false}")
    private boolean dedicatedImportWorker;

    private final JdbcTemplate jdbc;
    private final LoyaltyService loyalty;
    private final LoyaltyReconcileWork work;

    /** Processes committed work; each order's existing reconciliation remains idempotent. */
    @Scheduled(fixedDelayString = "${gokul.loyalty.reconcile-delay-ms:15000}")
    public void reconcile() {
        long started = MethodTiming.start(LoyaltyReconciler.class, "reconcile()");
        try {
            if (dedicatedImportWorker) return;
            for (var item : work.claim()) {
                try {
                    loyalty.reconcile(item.orderId());
                    work.complete(item);
                } catch (RuntimeException failure) {
                    work.failed(item);
                    log.error(
                            "Loyalty queued reconciliation failed: orderId={}",
                            item.orderId(),
                            failure);
                }
            }
        } finally {
            MethodTiming.finish(started, LoyaltyReconciler.class, "reconcile()");
        }
    }

    /** Checks a bounded historical slice so legacy records also receive repair coverage. */
    @Scheduled(fixedDelayString = "${gokul.loyalty.repair-delay-ms:300000}")
    public void repair() {
        long started = MethodTiming.start(LoyaltyReconciler.class, "repair()");
        try {
            if (!dedicatedImportWorker) work.repair();
        } finally {
            MethodTiming.finish(started, LoyaltyReconciler.class, "repair()");
        }
    }

    /** Expires stored balances; wallet reads still enforce expiry synchronously. */
    @Scheduled(fixedDelayString = "${gokul.loyalty.expiry-delay-ms:300000}")
    public void expire() {
        long started = MethodTiming.start(LoyaltyReconciler.class, "expire()");
        try {
            if (dedicatedImportWorker) return;
            var expiring =
                    jdbc.queryForList(
                            "SELECT DISTINCT l.environment,l.subject_id FROM loyalty_lots p JOIN"
                                + " loyalty_account_ledger l ON l.id=p.ledger_id LEFT JOIN"
                                + " loyalty_account_ledger origin ON origin.id=l.source_credit_id"
                                + " JOIN loyalty_accounts a ON a.environment=l.environment AND"
                                + " a.subject_id=COALESCE(origin.original_subject_id,l.original_subject_id)"
                                + " WHERE p.remaining>0 AND"
                                + " GREATEST(l.expires_at,a.last_qualifying_activity+(l.expiry_days*INTERVAL"
                                + " '1 day'))<=CURRENT_TIMESTAMP LIMIT 500");
            for (var account : expiring) {
                try {
                    loyalty.expireAccount(
                            (String) account.get("environment"),
                            (java.util.UUID) account.get("subject_id"));
                } catch (RuntimeException failure) {
                    log.error(
                            "Loyalty expiry reconciliation failed: environment={}, subjectId={}",
                            account.get("environment"),
                            account.get("subject_id"),
                            failure);
                }
            }
        } finally {
            MethodTiming.finish(started, LoyaltyReconciler.class, "expire()");
        }
    }
}
