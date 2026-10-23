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

## Versioned dated menu previews

`GOKUL_MENU_VERSIONED_PREVIEWS_ENABLED` defaults to `false`. Enable it in DEV after V137 migrates, exercise the checks below, then enable production explicitly. OFF retains the existing fresh preview coordinator; older backends ignore the new preview query parameter. Disable the setting and restart to roll back the optimisation; the additive migration may remain. Checkout, order creation, holds, payment confirmation, cancellation and stock expiry never use this cache.

The opt-in menu endpoint reads one compact fingerprint before reusing a completed preview. V137 gives each inventory allocation and pickup slot its own random update token, including direct SQL writes. Insertions/deletions change the fingerprint's membership. Configuration changes (catalog, service rules, inventory policies, pickup settings and tax categories) advance the existing transactional catalog revision. Feature values also form part of the fingerprint. There is no shared stock-counter row or growing change-log table. Allocation and slot scans are scoped by indexed branch/date relationships; measure their query plans with production-sized data.

On a miss, the fingerprint and full evaluation share a read-only, five-second, repeatable-read transaction. Publication follows successful commit. This prevents overlapping stock commits from labelling an older preview with a newer version. Entries include exact branch, date, product order, quantities and weights. Each replica retains at most 64 entries / 20,000 item-and-issue units; oversized previews bypass retention. Reuse ends at the earliest preparation cutoff, pickup start, local midnight or five minutes. A failed read does not fall back to an old successful stock decision.

Visible online menus poll `/api/branches/{branchId}/menu-state?date=...` about every two seconds. A one-second, bounded per-replica hint cache shares fingerprints across customers. Only a changed token refreshes the full preview; the first successful token also refreshes to close the initial-load race. Hidden/offline pages stop network checks, failures back off to 30 seconds, and 404 stops this watcher for the current branch/date. Existing minute, visibility and reconnect refreshes remain. Returned clock boundaries schedule an additional refresh. Cancellation prevents an old branch/date response from refreshing the new menu. Large menus use at most two concurrent 100-item batches, publishing only a complete successful result.

This provides updates in a few seconds under normal load, rather than a 60-second full-preview cadence; it is not an instantaneous delivery guarantee. A lightweight database check is still required to detect commits on another replica. A single active customer may make more lightweight checks than before; aggregate savings depend on active customers, replicas, menu size and mutation frequency. Initial cold reads still need database evaluation. Neon wake-up, network latency and production compute limits remain. Measure before claiming a particular latency or CU-hour reduction.

### Cache audit

| Area | Existing or proposed behaviour | Reason |
| --- | --- | --- |
| Menu catalog | Existing immutable version URL and bounded revision cache | Reuse until configuration changes |
| Current service status | Existing short cache with service-time boundary | Availability changes with the clock |
| Dated menu inventory | New opt-in version cache and shared change hints | Stock, holds, capacity and policies can change on any replica |
| Menu appearance / public offers | Keep existing reads and scheduled expiry handling for this release | Published visibility, schedules and benefit eligibility need their own complete revision contract |
| Portion groups / branch public metadata | Further configuration-cache candidates | Add explicit revision dependencies and scheduled visibility before caching longer |
| Cart / checkout / reservations | Fresh authoritative checks and database row locks | Cached displays cannot promise the last unit to concurrent customers |
| Payments / identity / staff permissions | Existing authoritative access and recovery paths | Public menu caching must not change financial or access decisions |

Before enabling production: check direct-SQL ready quantity, held/committed quantity, slot fill/release, item/catalog closure, service-rule edits, policy and pickup-setting edits, insert/delete/rollback invalidation, preparation cutoff, midnight, weighted quantities, branch/date switches, disabled-setting/old-server fallback and concurrent last-stock ordering. Use the existing last-stock concurrency integration regression as a release gate. The new integration tests cover V137 triggers against PostgreSQL; local root-only environments require CI for those tests.

### Versioned preview verification

Local validation: 25 targeted backend tests pass, including cache identity/expiry/bounds, fresh version probes, failed-commit publication, legacy/opt-in HTTP routing, preparation/midnight boundaries and existing preview deadlines/coalescing. Java compilation, formatting and timing coverage pass. All 246 frontend Node tests pass in UTC and America/Los_Angeles; TypeScript, ESLint and the production build pass. Browser checks cover unchanged-token request counts, changed stock disabling/re-enabling Add, preserved cart/pickup, and existing future-pickup/service-hour/large-menu regressions. PostgreSQL trigger and repeatable-read concurrency tests are compiled and await CI execution. The final clock-skew browser assertion is checked again on the rebuilt frontend before handoff.

### Preview correctness safeguards

Manual sold-out and missing-dependency closures stay excluded from menu-wide previews; only known temporal closures may reopen for a future pickup. Inventory invalidations coalesce while a preview or soonest-pickup search is running, so continuous stock updates cannot starve completed results. Changing the branch, pickup or cart still cancels obsolete work. Cutoff timers use fresh server observation timestamps and monotonic elapsed time, independent of the customer device wall clock; older servers retain the periodic refresh fallback. Checkout continues validating and reserving stock in PostgreSQL.

## Follow-up: measured caching and optional worker roles

See [cache verification and worker separation](cache-workers-and-menu.md) for the 23 October production observation, bounded read-only probe, cache-mode response header and private lookup metrics. Versioned-preview rollout remains explicitly opt-in. Worker roles now share one scheduling registration owner; legacy configuration classes no longer bypass the global switch. Combined remains the default.
