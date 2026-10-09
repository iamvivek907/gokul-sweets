-- Disposable CI connection only. Temporary tables never modify application data or indexes.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE growth_orders(id bigint PRIMARY KEY,branch_id bigint,order_status text,created_at timestamp,payload text);
CREATE TEMP TABLE growth_payments(id bigint PRIMARY KEY,order_id bigint,payment_status text,payload text);
INSERT INTO growth_orders SELECT n,n%100+1,(ARRAY['CONFIRMED','PICKED_UP','CANCELLED','PAYMENT_FAILED'])[((n/100)%4)+1],timestamp '2020-01-01'+n*interval '1 minute',repeat('x',250) FROM generate_series(1,100000) n;
INSERT INTO growth_payments SELECT n,(n-1)%10000+1,CASE WHEN n>290000 THEN 'PAID' ELSE 'FAILED' END,repeat('x',100) FROM generate_series(1,300000) n;
CREATE INDEX growth_orders_branch ON growth_orders(branch_id);
CREATE INDEX growth_orders_branch_status ON growth_orders(branch_id,order_status);
CREATE INDEX growth_orders_created ON growth_orders(created_at);
CREATE INDEX growth_payments_order ON growth_payments(order_id);
ANALYZE growth_orders;
ANALYZE growth_payments;
CREATE TEMP TABLE growth_reports(scenario text,phase text,plan jsonb);
CREATE FUNCTION pg_temp.growth_report(scenario text,phase text,query_text text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE result jsonb;
BEGIN
 EXECUTE 'EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) '||query_text INTO result;
 INSERT INTO growth_reports VALUES(scenario,phase,result);
END $$;
SELECT pg_temp.growth_report('branch-newest-page','before','SELECT * FROM growth_orders WHERE branch_id=1 ORDER BY created_at DESC LIMIT 20');
SELECT pg_temp.growth_report('branch-status-newest-page','before','SELECT * FROM growth_orders WHERE branch_id=1 AND order_status=''CONFIRMED'' ORDER BY created_at DESC LIMIT 20');
SELECT pg_temp.growth_report('latest-payment-status','before','SELECT p.order_id,p.payment_status FROM growth_payments p WHERE p.order_id IN (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20) AND p.id=(SELECT MAX(x.id) FROM growth_payments x WHERE x.order_id=p.order_id)');
CREATE TEMP TABLE growth_expected AS SELECT p.order_id,p.payment_status FROM growth_payments p WHERE p.order_id IN (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20) AND p.id=(SELECT MAX(x.id) FROM growth_payments x WHERE x.order_id=p.order_id);
CREATE INDEX idx_orders_branch_created ON growth_orders(branch_id,created_at DESC);
CREATE INDEX idx_orders_branch_status_created ON growth_orders(branch_id,order_status,created_at DESC);
CREATE INDEX idx_payments_order_latest_id ON growth_payments(order_id,id DESC);
SELECT pg_temp.growth_report('branch-newest-page','after','SELECT * FROM growth_orders WHERE branch_id=1 ORDER BY created_at DESC LIMIT 20');
SELECT pg_temp.growth_report('branch-status-newest-page','after','SELECT * FROM growth_orders WHERE branch_id=1 AND order_status=''CONFIRMED'' ORDER BY created_at DESC LIMIT 20');
SELECT pg_temp.growth_report('latest-payment-status','after','SELECT o.id,p.payment_status FROM growth_orders o JOIN LATERAL (SELECT payment_status FROM growth_payments WHERE order_id=o.id ORDER BY id DESC LIMIT 1) p ON true WHERE o.id IN (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20)');
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM growth_reports WHERE phase='after' AND scenario LIKE 'branch-%' AND (plan::text LIKE '%"Node Type": "Sort"%' OR plan::text NOT LIKE '%idx_orders_branch%')) THEN
  RAISE EXCEPTION 'Newest-first pages must use the matching branch index without a separate sort';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM growth_reports WHERE phase='after' AND scenario='latest-payment-status' AND plan::text LIKE '%idx_payments_order_latest_id%') THEN
  RAISE EXCEPTION 'Latest-payment query must use indexed per-order lookups';
 END IF;
 IF EXISTS((SELECT order_id,payment_status FROM growth_expected EXCEPT SELECT o.id,p.payment_status FROM growth_orders o JOIN LATERAL (SELECT payment_status FROM growth_payments WHERE order_id=o.id ORDER BY id DESC LIMIT 1) p ON true WHERE o.id IN (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20)))
 OR (SELECT count(*) FROM growth_expected)<>20 THEN RAISE EXCEPTION 'Latest-status results changed'; END IF;
END $$;
SELECT jsonb_build_object('scenario',scenario,'phase',phase,'orders',100000,'payments',300000,'execution_ms',plan->0->'Execution Time','shared_hits',plan->0->'Plan'->'Shared Hit Blocks','local_hits',plan->0->'Plan'->'Local Hit Blocks','rows',plan->0->'Plan'->'Actual Rows','plan',plan->0->'Plan') FROM growth_reports ORDER BY scenario,phase DESC;
SELECT jsonb_build_object('index',indexname,'bytes',pg_relation_size(indexname::regclass)) FROM pg_indexes WHERE tablename IN ('growth_orders','growth_payments') AND indexname IN ('idx_orders_branch_created','idx_orders_branch_status_created','idx_payments_order_latest_id');
ROLLBACK;
