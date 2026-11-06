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
4. Automatic approval is the default for new selections: confirm a guaranteed daily quantity once
   to publish it without daily approval. Existing saved approval choices are preserved. Manual-review
   mode remains an explicit option and creates drafts that require staff approval.
   Physical readiness remains a separate staff action where the policy requires it.
5. Save enabled settings. The automatic job runs once daily at 12:00 AM IST. Saving or adding
   configuration does not start a job. For initial setup or changes after the daily run, use
   Generate missing coverage now after saving, or wait for the next daily run. Both paths
   reconcile the full maintained window and include newly eligible configured policies.

All quantities use policy units: GRAM quantities are grams, not kilograms.

## Ownership and safe repeats

Both schedulers acquire the same branch-row lock before checking ownership or generating.
When the new flow is enabled for a branch, the legacy inventory scheduler skips that branch.
When disabled, the old scheduler resumes its existing rules. Manual APIs remain
available in both cases. The ownership guard is the only legacy scheduler behavior change.

Every check reconciles each selected weekday throughout the maintained calendar range, including
already-generated dates. Missing regular intervals within the configured hours are inserted;
existing intervals retain their times, capacity, bookings and active/disabled state. Overlapping
manual or disabled intervals block generation in their time range. To intentionally pause a slot,
disable it instead of deleting it: a deleted interval may be recreated on the next check.
Private occasion appointments neither claim nor satisfy regular coverage.
Existing date-tracking rows do not prevent recovery of a partial or missing timetable.
Database slot-write serialization and overlap protection also coordinate concurrent manual writers.

Inventory creation follows dates with active regular slots, within the product booking horizon
and effective storefront inventory horizon. While this flow is enabled, customer pickup discovery,
cart availability and checkout use the rolling calendar horizon (maintain days minus one day ahead),
capped by the storefront limit, instead of the manual advance-booking limit. Each item's policy can
still shorten that window. The branch pickup closed switch remains authoritative. Disabling this
flow restores the original manual booking window without editing the saved manual settings.
Only missing allocations are inserted. Existing
drafts, approved quantities, reservations, sales, wastage and manual overrides are not replenished
or overwritten. Inventory approval uses the existing audited inventory service.

Changing a schedule affects missing intervals; existing intervals are not resized or removed. It does not cancel reservations or retire
old future slots when the horizon shrinks. Review those through manual scheduling.

## Recovery and operations

The worker runs once daily at midnight (12:00 AM) in Asia/Kolkata. There is no startup
run or interval polling. Configuration changes after that run wait until the next daily
run unless an admin triggers Generate missing coverage now using saved settings.
Branch reconciliation is transactional: a failure rolls back its new slots and allocations,
records a failure message, and can be retried manually or at the next daily run. Other branches continue independently.
Last-attempt, last-success and summary fields are shown in the admin page.
Reload saved settings refreshes execution diagnostics and discards the edits present when clicked.
Editing and writes are blocked until reload settles; obsolete responses are ignored. Failed reloads
retain the last loaded configuration and can be retried. Daily generation does not depend on this
page being open or on an admin clicking any generation button.

Configuration:
- inventory.rolling.scheduler-enabled: defaults to true; branch plans still default to disabled.
- The daily cron is fixed to midnight IST; the former initial-delay-ms and poll-ms settings
  are no longer used by this worker.
- Dedicated import workers do not run this worker.

The application must be running at midnight IST for scheduled execution. If a run is missed
during downtime, use Generate missing coverage now after recovery, or wait for the next daily
run. Restarting the application does not trigger extra generation.
Pausing the worker globally while branch plans are enabled does not transfer ownership back
to the legacy scheduler. Disable a branch plan explicitly if that transfer is intended.

## Managing large catalogues and no-op runs

The dish picker renders 25 policies per page by default (10/25/50 choices), with local
name search and All / Selected / Needs attention views over the bounded branch catalogue.
Selections and quantity drafts are retained across pages and filters; saving submits the
whole selection. Invalid off-page quantities are revealed before a save is sent.
Browsing and help remain available to read-only staff without enabling write actions.

Generation activity is shown before the setup form, with saved selection counts and IST
timestamps. Draft changes are explicitly separated from the settings used by the worker.
Run messages count active pickup dates, dates without future regular slots, existing
allocations and skips due to unavailable policies, booking windows or insert checks.
Empty selections and a range without usable slots receive specific next steps. These
diagnostics never enable a dish, refill an allocation or rewrite an existing timetable.

## Automatic policy maximum quantities

Choose Inventory quantity source → Policy maximums and save the enabled branch plan.
The worker reevaluates current, configured branch policies on every check; individual
selection and guaranteed quantity entry are not needed. New eligible policies are picked
up automatically. Existing plans default to explicit selection until this mode is chosen.

Only online, branch-available physical policies whose dish and category are active, with a positive maximum greater than the
safety buffer qualify. Missing maximums, maximums at/below the buffer, invalid piece units,
manual/capacity policies and bulk-only dishes are skipped. The run report explains skips.
The maximum is the gross approved quantity: maximum 200 and buffer 50 produces approved
200, buffer 50 and 150 planned after buffer, before commitments/readiness/service checks.
The buffer is never deducted twice. New rows use the audited automatic approval path.
Physical readiness remains separate when required by the policy.

Saved manual selections are retained separately when switching modes. Unsaved manual
quantity edits are not used or saved by maximum mode. Policy changes affect missing future
allocations only; existing drafts, overrides and approved stock are never rewritten.

Repeated worker or manual Generate requests share the branch lock and unique item/date
allocation constraint. Inserts use ON CONFLICT DO NOTHING; approval occurs only for rows
inserted by that run. Existing holds, commitments, readiness, manual quantities and ledger
entries are not reset or replenished. Coverage follows active regular slot dates on selected
weekdays throughout the rolling horizon, capped by product and storefront booking limits.
As dates enter that horizon, later checks create missing allocations using current policy
maximums. Existing disabled/manual slot schedules and private occasion appointments retain
their protections. No admin page or daily button action is required for scheduled checks.

## Ready quantity and automatic recovery

An existing daily allocation is authoritative, including recorded ready quantity, an explicit zero,
manual drafts, unavailable/closed status and reservations. Reconciliation does not change it just
because a policy maximum changes or a check is repeated. Ready-stock availability continues to
use recorded ready stock capped by the approved quantity, less buffer and commitments.
For an item/date without an allocation, maximum mode creates an approved allocation from the
policy maximum; it does not invent ready stock or mark production ready. A maximum is a planning
limit, not evidence of physical stock. Policies requiring ready stock retain that requirement.
Missing slots and missing item/date allocations are recovered independently on every check.
The daily run automatically advances the horizon. Manual runs reconcile the same full window
without changing the next daily execution. Existing allocations are never topped up to replace sold or reserved quantity.

Inactive dishes and inactive categories are skipped in both quantity modes and remain visible
with a reason in admin. Reactivating them allows the next daily or manual run to create missing
allocations within the maintained window. Existing allocations are not rewritten.
