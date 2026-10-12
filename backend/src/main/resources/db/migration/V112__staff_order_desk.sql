ALTER TABLE branch_products ADD COLUMN early_preparation_allowed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kot_items ADD COLUMN sale_mode VARCHAR(20);
ALTER TABLE kot_items ADD COLUMN weight_grams INTEGER;
-- The order and product uniquely identify a line in orders created by the validated cart.
-- Avoid guessing for legacy duplicate product lines.
WITH snapshots AS (
 SELECT k.id kot_id,i.product_id,MIN(i.sale_mode) sale_mode,MIN(i.weight_grams) weight_grams
 FROM kot k JOIN order_items i ON i.order_id=k.order_id
 GROUP BY k.id,i.product_id HAVING COUNT(*)=1
)
UPDATE kot_items ki SET sale_mode=s.sale_mode,weight_grams=s.weight_grams
FROM snapshots s WHERE ki.kot_id=s.kot_id AND ki.product_id=s.product_id;
ALTER TABLE kot_items ADD CONSTRAINT kot_weight_snapshot_valid CHECK
 (sale_mode IS NULL OR sale_mode='UNIT' OR (sale_mode='WEIGHT' AND weight_grams IS NOT NULL AND weight_grams>0));
ALTER TABLE order_corrections DROP CONSTRAINT order_corrections_kind_check;
ALTER TABLE order_corrections ADD CONSTRAINT order_corrections_kind_check CHECK(kind IN ('CANCEL','TRANSFER','RESCHEDULE'));
