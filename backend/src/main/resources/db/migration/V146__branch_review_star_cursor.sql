-- Bounded star-filter pages without blocking review submissions.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reviews_branch_stars_cursor ON reviews(branch_id,overall_rating,id DESC) WHERE review_status='PUBLISHED' AND comment IS NOT NULL;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=current_schema() AND c.relname='idx_reviews_branch_stars_cursor' AND NOT i.indisvalid) THEN RAISE EXCEPTION 'Branch star review index is invalid; repair interrupted build before retrying'; END IF;
END $$;
