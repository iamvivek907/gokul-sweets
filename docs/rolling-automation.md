# Separate automatic pickup and inventory flow

Admin entry: Branch operations → Automatic slots & inventory.

This is opt-in for each branch, with separate schedule and quantity-template tables.
It does not update manual pickup settings, product policies, existing allocation quantities,
staff-recorded readiness, customer reservations, private occasion appointments or legacy automation rules.

## Configure

1. Select a branch and enter same-day opening/closing time, slot duration,
   orders per slot and 1–60 calendar days including today.
2. Select selling weekdays. Closed weekdays are skipped within that calendar horizon.
3. Choose the quantity source. New setups default to **Automatic · use policy maximums**:
   every eligible configured branch policy is included without individual selection. Existing
   saved plans retain their quantity source; use **Use policy maximums** to change a manual plan.
   Alternatively, choose individual dishes and quantities. Unsupported policies remain excluded.
4. Review the dish count, pickup date preview and any shorter booking windows. The storefront
   booking cap is returned by the backend; product and storefront caps can shorten inventory
   coverage even when slots span more dates. The page explains these limits without editing them.
   Dishes are paginated with search and included / excluded / shorter-window filters. Help icons
   explain configurable fields, quantities, buffers, approval and readiness.
5. Choose **Save & generate now** to save this setup and reconcile missing slots and daily stock
   immediately. **Save automation settings** only saves; the worker uses it at the next midnight
   IST run. Saving a paused setup does not generate stock. New manual selections default to
   automatic approval; existing approval choices are preserved. Manual review creates drafts.
   Automatic readiness defaults on for guaranteed automatic approvals; manual-review drafts and staff overrides remain separate.

The page separates the **saved setup** used by the worker from the **unsaved preview**. A saved
manual plan with no selected dishes cannot generate inventory, even if an unsaved maximum-mode
preview shows eligible dishes. The primary combined action avoids this empty-plan mistake.
An empty plan or zero eligible dates is explained explicitly rather than presented as stock success.
Saved coverage diagnostics and execution history are available in an expandable section below setup.

The combined action saves first and sends the returned plan version with generation. A failed
save never triggers generation. A failed generation preserves the successfully saved settings;
retrying the unchanged setup does not save again. The backend checks the expected version while
holding the branch lock, rejecting a stale reviewed setup before writing slots or allocations.
Older clients may omit this optional version parameter. The existing worker cadence is unchanged.

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

Saved stock status and expandable generation activity follow the setup, with saved selection
counts and IST timestamps. Draft changes are explicitly separated from the settings used by the worker.
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
Automatic readiness is recorded for guaranteed approvals when enabled. Turning it off retains staff readiness requirements.

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
policy maximum. With automatic readiness on, a policy requiring ready stock is also marked READY
with the guaranteed approved quantity through the audited readiness service. This is an explicit
branch guarantee, not a measurement or production sensor. Turn automatic readiness off for stock
that staff must physically count before accepting bookings.
Missing slots and missing item/date allocations are recovered independently on every check.
The daily run automatically advances the horizon. Manual runs reconcile the same full window
without changing the next daily execution. Existing allocations are never topped up to replace sold or reserved quantity.

Inactive dishes and inactive categories are skipped in both quantity modes and remain visible
with a reason in admin. Reactivating them allows the next daily or manual run to create missing
allocations within the maintained window. Existing allocations are not rewritten.

## Coverage health, overdue alerts and execution history

Admin audits coverage when loaded using bulk reads across the effective calendar window. It
never generates stock while checking health and adds no background polling. Missing intervals
and item/date allocations appear separately, with up to 15 slot and 15 dish examples. Disabled
intervals, elapsed times, unselected weekdays and product/storefront booking limits are respected.
Draft approval and physical readiness are separate from missing setup records; complete coverage
still requires normal stock, approval, applicable readiness and service checks before customer ordering.

A plan enabled before today's midnight is overdue if no successful check has completed since
midnight, with a 15-minute grace period. A successful manual run satisfies that day's check.
Newly enabled plans are due at the following midnight. Ordinary edits preserve the activation anchor and do not clear overdue alerts. Failed and interrupted runs raise an
inline admin alert. Authorized admins can retry saved settings; unsaved edits and read-only
access disable retry. Paused plans do not raise overdue alerts.

History records scheduled/manual triggers, authenticated staff username or system actor,
start/finish times, result, created counts, elapsed time, lock wait and lock-held time. Keep the
latest thirty completed runs per branch plus in-progress runs; the API returns at most thirty
recent entries. Failures are recorded after branch rollback. RUNNING entries older than an hour
are marked failed on the next invocation; health warns after ten minutes. Earlier last-success
timestamps remain valid after upgrade.

Generation retains an atomic branch transaction with a five-minute transaction deadline bounding
its database work and lock waits. Existing allocations and bookings are preserved. Timings identify
whether a branch needs smaller recoverable batches; this change does not introduce partial commits
or repeated generation attempts. The schedule mutex permits run-audit foreign keys while continuing
to serialize schedule writers and protect overlapping intervals.

V154 records a separate enabled-since timestamp, preserved on ordinary saves and while paused, and reset only when
automation is paused and enabled again. Existing enabled plans receive their last known saved
timestamp as a one-time upgrade anchor. New successes use database wall-clock completion time;
health also considers successful run-history completion timestamps, including older runs that
started before midnight and finished afterward.

The database activation trigger also handles flag changes from older application versions during
deployment. Disabling preserves the last activation anchor for history; re-enabling records a new
wall-clock timestamp. Never-enabled paused plans may have no activation anchor.

## Bulk selection and action feedback

Manual quantity mode offers Select all matching dishes and Clear matching selections. These
operate across every page in the current search/filter. Bulk selection adds only eligible,
selectable policies, preserves existing quantity/approval choices, and defaults new selections to
valid policy maximums with automatic approval. Without a valid maximum, quantity remains blank
and must be entered before saving. Clearing removes only matching selections. Read-only staff
cannot use either action. Automatic maximum mode continues to include eligible policies directly.

Save/generation feedback appears beside the action and is brought into view, including failures
and no-op explanations. Saving a paused plan confirms that this job will not run and explains
that existing stock/bookings are preserved and legacy scheduling resumes its existing rules.

## Guaranteed automatic readiness

Automatically mark guaranteed stock ready defaults ON for rolling branch plans, including existing
plans upgraded by V155. It is visible in setup and persisted independently of product policies.
The branch must guarantee these quantities. Product policies, the manual allocation flow, service
hours, booking limits and stock enforcement remain unchanged. An authorized admin can turn it off
and save to retain manual readiness for subsequently generated stock; disabling never revokes
already recorded readiness.

New automatically approved allocations for ready-stock policies are immediately marked READY
using their gross approved quantity. The existing readiness service records the audit and ledger;
the buffer is withheld once by normal availability calculation. Manual-review templates remain
DRAFT. Sold, held, committed or wasted quantity is never replenished by a repeat run.

On a daily or manual run, an existing allocation is upgraded only when it is an untouched APPROVED
row originally approved by SYSTEM_ROLLING_AUTOMATION, has zero initial readiness with no actual
ready timestamp, no holds/commitments/wastage, exactly one automation approval audit and no other
stock activity. The original approved quantity is used even if the policy maximum has increased.
A locked row and provenance checks serialize this with staff writes. Explicit staff zero readings,
manual approvals, readiness records, delays, closures and any previously used allocation are kept.
The generation summary counts readiness publications separately from newly created allocations.

After deployment, use Save & generate now to apply it immediately to eligible untouched existing
allocations, or wait for the next midnight IST run. The schedule is still once daily, with no polling.
Customer cart availability is integration-tested with ready-stock enforcement enabled. Recording
guaranteed readiness is not a guarantee against sold-out quantities, staff closures or other normal
ordering restrictions.

V156 adds allocation-first covering indexes for readiness provenance checks in the allocation
plan audit and stock transaction histories. Recovery can inspect just the matching allocation's
history instead of scanning unrelated history for every candidate. Non-allocation transactions
are excluded from the stock-history index. The staff-override checks and recovery rules are unchanged.
The provenance query was verified with normal PostgreSQL planner settings against 100,000 audit
rows and 200,000 stock-history rows: all three history lookups changed from sequential scans
to index-only scans, with the same eligibility result. These are isolated test results, not a
production latency guarantee.


Readiness recovery skips historical allocations whose stored inventory unit differs from the
current policy unit, before calling the readiness service. It never converts or relabels existing
quantities. The run summary counts skipped mismatches; coverage reports attention with bounded
per-dish/date examples showing both units. Other compatible allocations and new dates continue
normally, so one outdated gram/piece allocation cannot roll back the branch's generation.


Policy edits are serialized with a running rolling job: the branch transaction locks configured
policy rows in ID order before reading its workspace, and retains those locks through allocation
and readiness writes. An edit committed first is visible to the job's unit check; an edit that
arrives later waits until the job completes. The existing five-minute transaction limit bounds
the job's database work and lock waits. Product policies are not rewritten by the job. Policies inserted after the lock scan are deferred
to the next daily or manual check rather than used without a policy lock.
