# Database work and DEV idle operation

The production ordering, payment expiry, refund recovery, reservation expiry and notification schedules remain enabled by default. There is no query/compute savings guarantee: paid PostgreSQL can remain at its minimum compute all day even with fewer statements.

## Inventory tasks

Scheduled inventory-centre empty-queue checks back off from two seconds to a maximum of 60 seconds. A committed submission publishes a local wake-up hint so the next normal two-second tick checks the queue. Rollbacks do not wake the worker. Other instances discover work within the configured idle maximum; PostgreSQL tasks and leases remain the recovery authority. Manual `runBatch` is immediate. Network/database errors remain retryable on a later tick.

`GOKUL_INVENTORY_IDLE_MAX_MS` controls the maximum (minimum effective value 2000). A continuously empty default worker eventually performs about 1,440 checks/day instead of about 43,200, excluding startup/backoff transitions. This measures queue checks, not Neon CU-hour savings. No change is made to financial or reservation deadlines.

## Idle DEV

`GOKUL_SCHEDULED_JOBS_ENABLED` defaults to `true`. Set it to `false` only for an intentionally idle DEV deployment with no active test orders or refunds. This disables registration of **all** Spring scheduled callbacks, including payments, refunds, stock expiry, marketing, imports, cleanup and diagnostics. Existing service/API beans remain available, but background workflows will not progress. Restore `true` and restart before testing those workflows. This is a deployment setting; it is never toggled automatically by customer inactivity.

The older `GOKUL_BACKGROUND_JOBS_ENABLED` is narrower and remains compatible. Neither toggle stops browser requests, printer agents, external health monitors, startup migrations or connection-pool validation. Stop those sources or stop the idle DEV backend when a fully sleeping database is required. Consider `GOKUL_DB_MIN_IDLE=0` for DEV, and verify actual compute inactivity in Neon. Keep production scheduling ON.

## Reporting and notifications

V136 creates `idx_orders_updated_at` concurrently for changed-order lookups. Independent indexed extrema replace the combined MAX aggregate; current reporting checkpoint, minute cadence, summaries and reconciliation behaviour remain intact. An invalid interrupted concurrent index build is rejected for operator repair. The migration is additive; code rollback does not require removing it.

Customer bell and sound runtime share a single 30-second timer and request-scoped inbox reads. `GET /api/customer/identity/notifications?summary=true` returns only the latest 30 IDs/timestamps/read states and the existing distinct unread-target count. It omits message bodies and the full page's additional read-through query. It retains trusted-session/environment checks, no-store responses, guest rejection backoff, quiet hours and account-change cancellation. Older backends ignore the query parameter and return the compatible full page during a rolling deploy. The full notification history endpoint is unchanged.

## Retention

`GOKUL_CLEANUP_BATCH_LIMIT` defaults to 5000 events per customer/staff batch and is clamped to 100–10000. Preview exposes the same effective bound used by execution. The existing 45-second transaction deadline, FK-safe deletion, leases, financial/replay protections, manual preview/run, environment separation and rollback rules remain intact. Retention remains 90 days by default; owners configure it in System → Data cleanup. Set the batch limit back to 500 for the earlier throughput bound. There is no unbounded loop or automatic retry of an uncertain deletion.

## Menu readiness

The versioned catalog's branch `available` toggle is preserved separately as `catalogAvailable` in the frontend. With smart availability ON, a current-hour service closure does not remove a product from later pickup checks. Actual dated stock, ready quantity, preparation lead time, service windows, slot capacity and checkout validation still decide orderability. With smart availability OFF, existing current-hour behaviour is retained. Manual catalog-disabled products remain disabled.

## Release evidence

Compare idle statements/hour, daily CU-hours, reporting query plans, notification payload sizes and cleanup eligible counts before/after in DEV. Exercise current and future pickup, ready and unready stock, weighted minimums, service closure, manual item closure, stale pickup, concurrent last-stock reservations, notification account changes and payment/refund recovery. No production deployment or live stock mutation is part of this PR.

### Local verification on 22 October 2026

Backend compilation, Java formatting/timing coverage and 23 targeted unit tests passed. All 240 frontend Node tests, TypeScript, ESLint and the production build passed. Browser regressions passed at 320/390/1280px for future pickup with current service closed, manual catalog closure, expiry/midnight, cart preservation, late responses, service-hour boundaries, branch closure and refresh failures. Notification browser checks passed for explicit permissions, denied fallback, subscription/revocation, quiet hours, sound and cross-tab deduplication.

The existing production Rasgulla could be added to the local browser cart after pickup selection; no live stock was changed and no order/payment was submitted. [Live item verification](../qa/database-usage/live-ready-rasgulla.jpg). PostgreSQL integration tests (including 5,001-event cleanup and V136 migration) require the CI database; they were not run locally because this sandbox only maps UID 0 and PostgreSQL refuses to run as root. Local Mockito tests used a Byte Buddy startup agent because JVM self-attach is unavailable in this sandbox. GitHub CI remains the production readiness gate.
