-- Match branch-filtered newest-first pages without sorting the entire branch history.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_branch_created
    ON orders(branch_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_branch_status_created
    ON orders(branch_id, order_status, created_at DESC);
-- Latest-status lookup is defined by MAX(payment.id), not by payment timestamps.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_order_latest_id
    ON payments(order_id, id DESC);

-- A cancelled concurrent build can leave an invalid index. Do not silently accept it on retry.
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
        JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname=current_schema()
          AND c.relname IN ('idx_orders_branch_created','idx_orders_branch_status_created','idx_payments_order_latest_id')
          AND NOT i.indisvalid
    ) THEN
        RAISE EXCEPTION 'Growth read index is invalid; inspect and repair the interrupted index build before retrying migration';
    END IF;
END $$;
