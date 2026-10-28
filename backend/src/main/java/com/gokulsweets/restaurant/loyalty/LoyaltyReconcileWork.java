package com.gokulsweets.restaurant.loyalty;

import com.gokulsweets.restaurant.observability.MethodTiming;

import io.micrometer.core.instrument.MeterRegistry;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Durable, leased loyalty work; SQL triggers record changes in their original transactions. */
@Service
@RequiredArgsConstructor
public class LoyaltyReconcileWork {
    private final JdbcTemplate jdbc;
    private final MeterRegistry metrics;

    @org.springframework.beans.factory.annotation.Value("${gokul.loyalty.idle-max-ms:60000}")
    private long idleMaxMillis = 60000;

    private long nextPollNanos;
    private long idleMillis = 15000;

    /** Versioned ownership prevents acknowledgements from deleting newer committed work. */
    public record Item(long orderId, long version, UUID lease) {}

    /** Claims at most 50 due orders without holding queue locks during order reconciliation. */
    public synchronized List<Item> claim() {
        long started = MethodTiming.start(LoyaltyReconcileWork.class, "claim()");
        try {
            if (nextPollNanos != 0 && System.nanoTime() - nextPollNanos < 0) return List.of();
            var lease = UUID.randomUUID();
            var items =
                    jdbc.query(
                            """
WITH due AS (SELECT order_id FROM loyalty_reconcile_work
  WHERE next_attempt_at<=CURRENT_TIMESTAMP
   AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)
  ORDER BY next_attempt_at,order_id LIMIT 50 FOR UPDATE SKIP LOCKED)
UPDATE loyalty_reconcile_work w SET lease_token=?,lease_until=CURRENT_TIMESTAMP+INTERVAL '5 minutes'
FROM due WHERE w.order_id=due.order_id RETURNING w.order_id,w.version,w.lease_token
""",
                            (rs, n) ->
                                    new Item(
                                            rs.getLong(1),
                                            rs.getLong(2),
                                            rs.getObject(3, UUID.class)),
                            lease);
            metrics.counter("gokul.loyalty.queue.polls").increment();
            metrics.counter("gokul.loyalty.queue.claimed").increment(items.size());
            idleMillis =
                    items.isEmpty()
                            ? Math.min(Math.max(15000, idleMaxMillis), idleMillis * 2)
                            : 15000;
            nextPollNanos =
                    System.nanoTime()
                            + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(idleMillis);
            return items;
        } finally {
            MethodTiming.finish(started, LoyaltyReconcileWork.class, "claim()");
        }
    }

    /** Acknowledges only the claimed version; preserves and unlocks later changes. */
    @Transactional
    public void complete(Item item) {
        long started = MethodTiming.start(LoyaltyReconcileWork.class, "complete(Item)");
        try {
            jdbc.update(
                    "DELETE FROM loyalty_reconcile_work WHERE order_id=? AND version=? AND"
                            + " lease_token=?",
                    item.orderId(),
                    item.version(),
                    item.lease());
            jdbc.update(
                    "UPDATE loyalty_reconcile_work SET lease_token=NULL,lease_until=NULL,attempts=0"
                            + " WHERE order_id=? AND lease_token=?",
                    item.orderId(),
                    item.lease());
        } finally {
            MethodTiming.finish(started, LoyaltyReconcileWork.class, "complete(Item)");
        }
    }

    /** Keeps failed work retryable and does not postpone a newer order change. */
    public void failed(Item item) {
        long started = MethodTiming.start(LoyaltyReconcileWork.class, "failed(Item)");
        try {
            jdbc.update(
                    """
UPDATE loyalty_reconcile_work SET lease_token=NULL,lease_until=NULL,
  next_attempt_at=CASE WHEN version=? THEN CURRENT_TIMESTAMP+
    (LEAST(300,15*POWER(2,LEAST(attempts,5))) * INTERVAL '1 second') ELSE CURRENT_TIMESTAMP END,
  attempts=CASE WHEN version=? THEN LEAST(attempts+1,1000) ELSE 0 END
WHERE order_id=? AND lease_token=?
""",
                    item.version(),
                    item.version(),
                    item.orderId(),
                    item.lease());
            metrics.counter("gokul.loyalty.queue.failures").increment();
        } finally {
            MethodTiming.finish(started, LoyaltyReconcileWork.class, "failed(Item)");
        }
    }

    /** Repairs at most 1000 historical orders per sweep, serialized across backend replicas. */
    @Transactional
    public void repair() {
        long started = MethodTiming.start(LoyaltyReconcileWork.class, "repair()");
        try {
            var rows =
                    jdbc.queryForList(
                            "SELECT last_order_id FROM loyalty_repair_cursor WHERE id=true FOR"
                                    + " UPDATE SKIP LOCKED",
                            Long.class);
            if (rows.isEmpty()) return;
            long cursor = rows.getFirst();
            jdbc.update(
                    """
    WITH candidates AS MATERIALIZED (SELECT * FROM orders WHERE id>? ORDER BY id LIMIT 1000)
    INSERT INTO loyalty_reconcile_work(order_id)
SELECT o.id FROM candidates o WHERE
 (EXISTS(SELECT 1 FROM loyalty_holds h WHERE h.order_id=o.id AND h.state<>'RELEASED') AND
  (o.order_status IN ('CANCELLED','PAYMENT_FAILED') OR EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND
    (p.payment_status='REFUNDED' OR (p.payment_status='PAID' AND EXISTS(SELECT 1 FROM loyalty_holds h WHERE h.order_id=o.id AND h.state='RESERVED'))))))
 OR (o.loyalty_enrolled AND EXISTS(SELECT 1 FROM verified_order_ownership own WHERE own.order_id=o.id) AND
   ((o.order_status IN ('PICKED_UP','DELIVERED') AND EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID') AND
     NOT EXISTS(SELECT 1 FROM loyalty_account_ledger l WHERE l.order_id=o.id AND l.kind='EARNED')) OR
    ((o.order_status='CANCELLED' OR EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='REFUNDED')) AND
      EXISTS(SELECT 1 FROM loyalty_account_ledger l WHERE l.order_id=o.id AND l.kind IN ('EARNED','WELCOME')) AND
      NOT EXISTS(SELECT 1 FROM loyalty_account_ledger l WHERE l.order_id=o.id AND l.kind='REVERSED'))))
 ORDER BY o.id ON CONFLICT(order_id) DO NOTHING
""",
                    cursor);
            Long last =
                    jdbc.queryForObject(
                            "SELECT COALESCE(MAX(id),0) FROM (SELECT id FROM orders WHERE id>?"
                                    + " ORDER BY id LIMIT 1000) page",
                            Long.class,
                            cursor);
            jdbc.update("UPDATE loyalty_repair_cursor SET last_order_id=? WHERE id=true", last);
            metrics.counter("gokul.loyalty.repair.pages").increment();
        } finally {
            MethodTiming.finish(started, LoyaltyReconcileWork.class, "repair()");
        }
    }
}
