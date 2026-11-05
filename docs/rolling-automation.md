# Separate automatic pickup and inventory flow

Admin entry: Branch operations → Automatic slots & inventory.

This is opt-in for each branch, with separate schedule and quantity-template tables.
It does not update manual pickup settings, product policies, existing allocation quantities,
readiness, customer reservations, private occasion appointments or legacy automation rules.

## Configure

1. Select a branch and enter same-day opening/closing time, slot duration,
   orders per slot and 1–60 calendar days including today.
2. Select selling weekdays. Closed weekdays are skipped within that calendar horizon.
3. Select only existing inventory policies for that branch and configure their quantities.
   Items without policies, bulk-only products, capacity-only/manual-control policies and unselected
   policies are not automated. A previously selected policy that becomes unsupported stays visible
   with an explanation and can be removed. Pausing retains existing templates without requiring
   quantities to match subsequently changed policy limits or units; enabling revalidates supported
   selections. Newly selecting unsupported policies is rejected by the API.
4. Choose draft creation or explicitly approve a guaranteed quantity for each selected policy.
   Physical readiness remains a separate staff action where the policy requires it.
5. Save enabled settings, then use Generate missing coverage now for the initial run.

All quantities use policy units: GRAM quantities are grams, not kilograms.

## Ownership and safe repeats

Both schedulers acquire the same branch-row lock before checking ownership or generating.
When the new flow is enabled for a branch, the legacy inventory scheduler skips that branch.
When disabled, the old scheduler resumes its existing rules. Manual APIs remain
available in both cases. The ownership guard is the only legacy scheduler behavior change.

New regular slots are created only for dates without any existing regular slots. Existing
manual dates, including disabled slots, are preserved. Private occasion appointments do not
claim normal dates. A generated date is recorded once, so later setting changes or manual
deletion do not recreate its timetable. Use manual pickup scheduling to change existing dates.
Database slot-write serialization prevents a concurrent manual generator from creating an
overlapping automatic timetable. Exact existing intervals remain reusable by the manual generator.

Inventory creation follows dates with active regular slots, within the product booking horizon
and effective storefront inventory horizon. While this flow is enabled, customer pickup discovery,
cart availability and checkout use the rolling calendar horizon (maintain days minus one day ahead),
capped by the storefront limit, instead of the manual advance-booking limit. Each item's policy can
still shorten that window. The branch pickup closed switch remains authoritative. Disabling this
flow restores the original manual booking window without editing the saved manual settings.
Only missing allocations are inserted. Existing
drafts, approved quantities, reservations, sales, wastage and manual overrides are not replenished
or overwritten. Inventory approval uses the existing audited inventory service.

Changing a schedule affects newly generated dates. It does not cancel reservations or retire
old future slots when the horizon shrinks. Review those through manual scheduling.

## Recovery and operations

The worker runs 30 seconds after application startup and every ten minutes thereafter.
Branch reconciliation is transactional: a failure rolls back its new slots and allocations,
records a failure message, and is retried later. Other branches continue independently.
Last-attempt, last-success and summary fields are shown in the admin page.
Reload saved settings refreshes execution diagnostics and discards unsaved form edits.

Configuration:
- inventory.rolling.scheduler-enabled: defaults to true; branch plans still default to disabled.
- inventory.rolling.initial-delay-ms: defaults to 30000.
- inventory.rolling.poll-ms: defaults to 600000.
- Dedicated import workers do not run this worker.

The application must be running for scheduled execution. Startup catch-up fills missed coverage
after downtime; it does not guarantee availability while the service is offline.
Pausing the worker globally while branch plans are enabled does not transfer ownership back
to the legacy scheduler. Disable a branch plan explicitly if that transfer is intended.
