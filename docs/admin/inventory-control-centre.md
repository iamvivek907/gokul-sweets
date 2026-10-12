# Inventory control centre

Open **Inventory → Inventory control centre** (`/admin/inventory/centre`). The old setup, allocation, readiness and automation pages are unchanged. Staff need MENU_MANAGE, INVENTORY_VIEW and INVENTORY_MANAGE and access to the branch.

1. Choose a branch and an IST date range, then load items. The catalogue is fetched in pages of 50; the screen shows 25 items per page.
2. Choose the selling method. Existing policies retain buffers, ceilings, shelf life and lead times. Explicitly select Apply selling method if changing an existing policy. Manual Unavailable settings remain unless Explicitly open selected items is checked.
3. Select individual items, all search matches, or all variants of an existing portion group. Each variant has independent quantities. For piece products enter whole pieces; weight products use kg and are converted to grams.
4. Enter planned daily allocations. Prepared stock for today also requires a verified physical quantity, at most the allocation. Future dates receive plans, never fabricated physical-ready confirmations. Made-to-order capacity does not require physical ready stock.
5. Optionally set common service hours and weekdays. These are eligible pickup hours, not the time customers must place their order. A customer can order at 4 AM for an 11 AM pickup if stock and pickup capacity permit. Hours repeat weekly in IST: opening inclusive, closing exclusive; overnight windows belong to the opening weekday. Existing sold-out flags and ingredient dependencies remain. Enforcement must be enabled for the branch; enabling it also activates its previously saved rules. Stock and manual availability still determine whether an item can be ordered. To change only hours, disable Apply inventory quantities.
6. Review and submit once. Drafts are saved in this browser. The job itself is durable in PostgreSQL. Progress survives refresh and server restart; Recent backend jobs recovers progress on another browser. Retrying an identical submission uses the same identifier and cannot execute the plan twice.

## Backend limits and failure handling

- At most 500 products, 60 days and 10,000 item-date allocations per job. At most one active job per branch and three globally. This is bounded date-range planning; existing recurring automation is unchanged.
- Admission takes a database advisory lock and performs bounded version reads and task inserts. Workers claim with SKIP LOCKED, process five products per poll, and commit each product's entire date range atomically with its task result. Work for different products can succeed independently.
- A worker crash rolls back uncommitted stock writes; its ten-minute lease can then be reclaimed. After three interrupted attempts the item fails for manual review. Completed tasks are never reclaimed.
- Permissions and branch access are rechecked when the job executes. Version conflicts, changed selling units, changed groups, paused/closed dates, safety buffers, stock limits, holds and commitments prevent unsafe overwrites.
- Failed results explain known validation errors. With the original draft, use Reload failed items only to fetch current versions, exclude successful items and clear physical-ready confirmations for another stock check. Recovered jobs without their original draft require a fresh reviewed plan, preventing guesses about old quantities and dates. Never repeat successful items merely because another item failed.
- Logs include job, branch and actor at submission, job/task/branch at item commit or rollback, batch duration, and cleanup counts. Error logs retain the exception; customer/staff responses do not expose internal database errors.

Properties: `inventory.centre.worker-enabled` (default true); `inventory.centre.poll-ms` (default 2000); `inventory.centre.cleanup-ms` (default 3600000). Disabling the worker stops execution and cleanup, not admission; monitor queue age before enabling this override.

## Retention

Successful work payloads are cleared on commit. Completed successful task details older than 30 days are deleted in batches of 1,000 only after the whole job completes. Failed payloads older than 90 days are cleared; error results and job summary counts remain. Recent jobs shows the latest ten jobs per branch.

Existing allocations, reservations, stock transactions, automation history, customer orders and audits are **not deleted**. They have operational, reconciliation or audit uses; no unused-history deletion has been proven safe. This avoids turning bulk processing into loss of financial or stock history. Queue cleanup does not undo customer stock changes or alter grouping.

## Verification

Backend integration tests cover independent grouped stock, preservation of sold-out flags/dependencies/manual unavailability, hours-only operations, future readiness, idempotent submission, rollback across dates, expired leases/concurrent workers, revoked permissions, queue limits and retention.

The production browser suite covers 320px, 390px and 1280px, draft restoration, a lost acknowledgement and identical retry, job recovery, kg-to-gram conversion, selection of grouped variants, and service-hours-only updates. Screenshots are uploaded with the staff-bulk CI evidence.
