# Daily data cleanup

Refresh locks the settings form until loading finishes; reads and mutations cannot overlap, and leaving the page aborts requests.

Owner admin: **System → Data cleanup** (`/admin/data-cleanup`). Default: enabled, daily 03:30 IST, retention 90 days (minimum 30). Configuration and latest results persist separately for DEV and PROD. Save settings, preview the next batch, then run manually if needed. Manual execution works with the schedule paused. A stale settings revision is rejected.

## Deletion allowlist

- Read customer routine order updates: CONFIRMED, PREPARING, READY_FOR_PICKUP, READY_FOR_DELIVERY, OUT_FOR_DELIVERY, READY_TIME_CHANGED. Both creation and read timestamps must precede the retention cutoff. The order must be PICKED_UP or DELIVERED, with its last update before the cutoff.
- Staff order reminders: NEW_ORDER, PREPARATION_SOON, PREPARATION_DUE, PREPARATION_OVERDUE, READY_OVERDUE. Creation and any scheduled timestamp must precede the cutoff; the completed order must also be old.
- Delivery receipts and staff read records belonging to those exact eligible events, deleted before their parent in the same transaction.

Any pending/unknown delivery state or active delivery lease protects the entire parent event. Customer completion events remain because their IDs are replay boundaries used to avoid acknowledging later refund messages. No deletion of payment/refund messages, unread customer messages, occasion messages, unknown kinds, active/recent orders, orders themselves, payments, customer identities, inventory ledgers/allocations, audits, or idempotency keys.

## Operational limits

Each run removes at most 500 customer events and 500 staff events, plus their children. Preview counts refer to the next bounded batch and can change before execution. A large backlog drains over successive daily/manual runs. Only the latest result is stored; no growing run-history table is introduced. Existing inventory-task and expired-security-bucket maintenance remains separate.

A minute poll catches up on the current IST day if the server starts after the configured time. It does not wake a sleeping hosting instance or replay every missed day. Failed daily runs do not retry every minute; use manual retry or wait for the next day. Dedicated import workers skip scheduling.

A five-minute persisted lease prevents overlapping instances; a crashed expired RUNNING claim may recover. The cleanup transaction locks the settings row and event/order rows. Locked candidates are skipped. It has a 45-second transaction deadline, 10-second statement timeout and 2-second lock timeout. Child deletion, parent deletion and success counts commit together. A confirmed rollback records failure. Commit-response errors are reconciled against the same persistent run token; committed success is returned without repeating deletion. An unavailable or superseded result reports uncertainty and preserves the existing result/lease; server logs contain diagnostics. Changing config while a claim is active affects future runs, not its captured retention.

Deletion frees space for PostgreSQL reuse through normal autovacuum; the table's reported disk size may not immediately shrink. This job never runs VACUUM FULL or rebuilds tables, which would lock normal operations.

## Verification

`DataCleanupIntegrationTest` creates a unique disposable PostgreSQL schema using CI's DATABASE_URL and drops only that schema. It tests environment separation, protected records, child ordering/FKs, read-only preview, bounded batches, manual execution while paused, conflicting claims, stale config and atomic rollback. Unit tests cover IST daily eligibility/crash recovery and owner-only authorization. `frontend/tests/data-cleanup.browser.mjs` covers owner settings, preview-before-run, failure feedback, mobile layout and non-owner denial.
