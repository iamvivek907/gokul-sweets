-- Read-only diagnostics. Save two snapshots and compare deltas over the same traffic period.
-- These counters are NOT CU-hours; Neon usage is the source for allocated compute usage.
SELECT clock_timestamp() AS observed_at, datname, numbackends,
       xact_commit, xact_rollback, blks_read, blks_hit,
       tup_returned, tup_fetched, tup_inserted, tup_updated, tup_deleted,
       deadlocks, stats_reset
FROM pg_stat_database WHERE datname=current_database();

SELECT state, count(*) AS connections
FROM pg_stat_activity WHERE datname=current_database() GROUP BY state;

SELECT count(*) AS queued_loyalty_orders,
       count(*) FILTER (WHERE next_attempt_at<=CURRENT_TIMESTAMP
         AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)) AS due,
       count(*) FILTER (WHERE attempts>0) AS retrying
FROM loyalty_reconcile_work;

-- Optional: run only if pg_stat_statements is installed and you have permission.
-- No SQL text, parameters, names or customer data are returned.
-- SELECT queryid,calls,total_exec_time,mean_exec_time,rows,shared_blks_read,shared_blks_hit
-- FROM pg_stat_statements WHERE dbid=(SELECT oid FROM pg_database WHERE datname=current_database())
-- ORDER BY total_exec_time DESC LIMIT 20;
