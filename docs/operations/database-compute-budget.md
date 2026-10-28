# Database workload and Neon Free budget

This change reduces repeated work; it does not guarantee a monthly CU-hour saving.
CU-hours measure allocated compute over active time, not query count. An always-active
0.25-CU endpoint uses 180 CU-hours in a 30-day month, exceeding Neon's current
100-CU-hour Free allowance. Scale-to-zero cannot be disabled on Free. Production's
critical expiry/refund schedules still query regularly and can keep it active.
Do not disable those schedules to make a live-order deployment fit Free.

## Implemented workload reductions

- Order, payment, ownership and loyalty-hold changes enqueue order IDs transactionally.
  The existing synchronous loyalty reconciliation remains unchanged. The durable queue
  repairs missed/failed callbacks with at most 50 leased orders per batch. Processing
  does not hold queue locks while acquiring order locks. Versioned acknowledgements
  cannot delete later changes. Failed work retries with bounded delay; expired leases
  recover after five minutes. Empty polls back off from 15 to at most 60 seconds.
- The migration performs one reconciliation-candidate backfill so pre-existing missed
  rewards/holds do not wait behind the historical cursor. This is a one-time historical
  read during deployment; schedule migration appropriately for very large histories.
- Historical repair examines at most 1000 order IDs every five minutes using a persisted,
  wrapping cursor. It never runs the old predicate over unbounded order history every
  15 seconds. A complete historical pass takes proportionally longer as history grows.
- Stored wallet-expiry maintenance runs every five minutes. Wallet access and order
  reconciliation still enforce expiry synchronously through the existing LoyaltyService.
- Only exact, query-free GET /api/branches and /api/branches/{numeric-id} display responses
  share a process-local cache for three seconds. There are at most 128 entries, 256 KiB
  per response, and 32 coordination locks. Errors, cookies and oversized responses are
  not retained. Successful admin writes clear the cache; a generation check prevents an
  in-flight pre-write request repopulating it. Other instances may show display data for
  the remaining TTL (maximum five seconds if configured). The cache never participates
  in authoritative branch, stock, quote, reservation or payment validation.

## Settings

Defaults are production-safe workload controls, not a Free-plan always-on configuration:

| Variable | Default | Meaning |
|---|---:|---|
| GOKUL_LOYALTY_IDLE_MAX_MS | 60000 | Maximum empty reconciliation-queue poll delay |
| GOKUL_LOYALTY_REPAIR_DELAY_MS | 300000 | Bounded historical repair interval |
| GOKUL_LOYALTY_EXPIRY_DELAY_MS | 300000 | Background wallet-expiry interval |
| GOKUL_BRANCH_DISPLAY_CACHE_MS | 3000 | Public display cache; 0 disables, capped at 5000 |

For an unused DEV deployment with no outstanding orders, holds, refunds or queued tasks,
the existing GOKUL_SCHEDULED_JOBS_ENABLED=false permits the database to idle when nothing
else queries it. This is NOT a live-PROD cost switch. Restore scheduling before accepting
work and allow pending work to drain. Development and production computes in the same
Neon project share that project's allowance; separate projects have their own limits.

## Measure before choosing compute limits

Collect 24 hours of idle/normal traffic and a representative peak. Compare the same
traffic before and after deployment; do not manufacture a CU-hour forecast from visits.
Record active hours, allocated compute over time, daily CU-hours and other project
computes in Neon. Extrapolate only comparable days: seven-day CU-hours / 7 * days/month.
Leave headroom for traffic growth and migrations. A 100-CU-hour / 30-day budget averages
3.33 CU-hours/day, equivalent to 13.33 active hours/day at exactly 0.25 CU, before any
larger-compute usage or additional computes.

The internal Micrometer registry now includes:

- gokul.browsing.branch.cache.hits and .misses
- gokul.loyalty.queue.polls, .claimed and .failures
- gokul.loyalty.repair.pages

Existing HTTP timing, Hikari connection-wait metrics and method timing remain available.
No public actuator endpoint or customer/order data logging is added.

If pg_stat_statements is available, capture deltas of calls, total_exec_time, rows and
shared_blks_read for normalized query IDs. Inspect the largest total-time and highest-call
queries. Do not reset shared production statistics simply to take a measurement. Use
EXPLAIN (ANALYZE, BUFFERS) on a representative staging dataset first: ANALYZE executes
the query. Examine the queue's due-work index and the bounded repair primary-key range;
existing order/payment/ownership/ledger indexes support related-record lookups.

Do not add indexes indiscriminately, cache checkout acceptance, expose SQL text/customer
values in diagnostics, or add another database compute just to run the same pollers.
