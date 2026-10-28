-- Transactional work recording; no external broker or lost after-commit callbacks.
CREATE TABLE loyalty_reconcile_work (
 order_id bigint PRIMARY KEY REFERENCES orders(id) ON DELETE CASCADE,
 version bigint NOT NULL DEFAULT 1,
 attempts integer NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 lease_until timestamptz,
 lease_token uuid
);
CREATE INDEX loyalty_reconcile_due ON loyalty_reconcile_work(next_attempt_at,order_id);
CREATE TABLE loyalty_repair_cursor (
 id boolean PRIMARY KEY DEFAULT true CHECK(id),
 last_order_id bigint NOT NULL DEFAULT 0
);
INSERT INTO loyalty_repair_cursor(id) VALUES(true);
CREATE FUNCTION enqueue_loyalty_reconciliation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE oid bigint;
BEGIN
 IF TG_TABLE_NAME='orders' THEN
  IF TG_OP='INSERT' AND NOT NEW.loyalty_enrolled THEN RETURN NEW; END IF;
  oid:=NEW.id;
 ELSE oid:=NEW.order_id; END IF;
 IF EXISTS(SELECT 1 FROM orders o WHERE o.id=oid AND
   (o.loyalty_enrolled OR EXISTS(SELECT 1 FROM loyalty_holds h WHERE h.order_id=oid))) THEN
  INSERT INTO loyalty_reconcile_work(order_id) VALUES(oid)
  ON CONFLICT(order_id) DO UPDATE SET version=loyalty_reconcile_work.version+1,
    next_attempt_at=CURRENT_TIMESTAMP;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER loyalty_order_work AFTER INSERT OR UPDATE ON orders
 FOR EACH ROW EXECUTE FUNCTION enqueue_loyalty_reconciliation();
CREATE TRIGGER loyalty_payment_insert_work AFTER INSERT ON payments
 FOR EACH ROW EXECUTE FUNCTION enqueue_loyalty_reconciliation();
CREATE TRIGGER loyalty_payment_update_work AFTER UPDATE OF payment_status ON payments
 FOR EACH ROW WHEN (OLD.payment_status IS DISTINCT FROM NEW.payment_status)
 EXECUTE FUNCTION enqueue_loyalty_reconciliation();
CREATE TRIGGER loyalty_owner_work AFTER INSERT OR UPDATE ON verified_order_ownership
 FOR EACH ROW EXECUTE FUNCTION enqueue_loyalty_reconciliation();
CREATE TRIGGER loyalty_hold_insert_work AFTER INSERT ON loyalty_holds
 FOR EACH ROW EXECUTE FUNCTION enqueue_loyalty_reconciliation();
CREATE TRIGGER loyalty_hold_update_work AFTER UPDATE OF state ON loyalty_holds
 FOR EACH ROW WHEN (OLD.state IS DISTINCT FROM NEW.state)
 EXECUTE FUNCTION enqueue_loyalty_reconciliation();
