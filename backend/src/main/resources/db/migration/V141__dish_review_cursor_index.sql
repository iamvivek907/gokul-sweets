-- Avoid blocking review submissions while indexing a large review history.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_review_items_dish_cursor ON review_items(product_id,review_id,rating);
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
 JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=current_schema()
 AND c.relname='idx_review_items_dish_cursor' AND NOT i.indisvalid) THEN
 RAISE EXCEPTION 'Dish review index is invalid; repair the interrupted build before retrying';
 END IF;
END $$;
