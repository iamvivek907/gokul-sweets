-- Separate from trigger DDL so its write-blocking locks are released before the historical read.
-- Bootstrap existing repairable work before switching away from the frequent history scan.
INSERT INTO loyalty_reconcile_work(order_id)
SELECT o.id FROM orders o WHERE
 (EXISTS(SELECT 1 FROM loyalty_holds h WHERE h.order_id=o.id AND h.state<>'RELEASED') AND
  (o.order_status IN ('CANCELLED','PAYMENT_FAILED') OR EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND
    (p.payment_status='REFUNDED' OR (p.payment_status='PAID' AND EXISTS(SELECT 1 FROM loyalty_holds h WHERE h.order_id=o.id AND h.state='RESERVED'))))))
 OR (o.loyalty_enrolled AND EXISTS(SELECT 1 FROM verified_order_ownership own WHERE own.order_id=o.id) AND
   ((o.order_status IN ('PICKED_UP','DELIVERED') AND EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='PAID') AND
     NOT EXISTS(SELECT 1 FROM loyalty_account_ledger l WHERE l.order_id=o.id AND l.kind='EARNED')) OR
    ((o.order_status='CANCELLED' OR EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.payment_status='REFUNDED')) AND
      EXISTS(SELECT 1 FROM loyalty_account_ledger l WHERE l.order_id=o.id AND l.kind IN ('EARNED','WELCOME')) AND
      NOT EXISTS(SELECT 1 FROM loyalty_account_ledger l WHERE l.order_id=o.id AND l.kind='REVERSED'))))
 ON CONFLICT(order_id) DO NOTHING;
