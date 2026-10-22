-- Bound changed-order reporting reads as the order history grows.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_updated_at ON orders(updated_at);
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
        JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=current_schema()
        AND c.relname='idx_orders_updated_at' AND NOT i.indisvalid) THEN
        RAISE EXCEPTION 'Reporting update index is invalid; repair the interrupted build before retrying';
    END IF;
END $$;
