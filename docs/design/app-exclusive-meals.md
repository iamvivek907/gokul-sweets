# App-exclusive meals: agreed product direction and implementation contract

Status: implementation in review in PR #254, with backend/PostgreSQL and browser validation in progress. The sections below describe the product contract. Current implementation limits are recorded at the end.

The owner selected **Special meal price**: admin sets a base meal price and optional
upgrade charges. Selected dishes consume their own inventory. The feature should
provide a useful app-exclusive ordering experience; increased sales are a hypothesis
to measure, not a promised outcome.

## Customer experience

- Show a compact App-exclusive meals collection alongside the existing food-first menu.
  Keep the existing dish/card grid, categories, search and ordering controls consistent.
- Open a meal card in the shared sheet: floating circular Close, stable meal heading,
  independently scrolling options, and a fixed quantity/price/Add meal footer. Use the
  shop's maroon branding, emerald selection controls and existing type/spacing tokens.
- Present each configured group in a white rounded panel, with actual requirements:
  Choose your main (choose one), Choose sides (choose one or two), optional drink or
  sweet. These are examples; never publish invented dishes or assumed shop offerings.
- Show selected state, any upgrade price and remaining required selections. Enable Add
  only when the selection is valid, and explain what remains to choose.
- Show the exact selected meal price plus upgrade charges. Only show a crossed-out
  comparison total when it is the real current price of the same component portions.
  Final tax and pickup/payment charges remain separately visible before payment.
- Preserve the meal as an editable, removable cart group with its chosen dishes and
  quantity. Multiple differently configured meals and ordinary dishes must coexist.
  Removing a meal must never remove an independently ordered dish with the same SKU.
- Use the same Add/explicit-Close feedback rules and reduced-motion behavior as #254.
  All options and Close remain keyboard/screen-reader accessible. Hindi covers system
  guidance; admin-authored dish names/translations stay authoritative.

## Admin configuration

Each branch owns a bounded collection of draft/published meals. Configuration includes:

- Name, Hindi name, description, actual image/focal point and display order.
- Base price, enabled state, optional publish dates in IST and branch availability.
- Ordered choice groups with names, minimum and maximum selections and required state.
- Options referencing real branch SKUs, exact included quantity/grams and any upgrade
  charge. Portions may differ from the retail minimum only through explicit, validated
  meal-portion configuration, never a client-side override of retail validation.
- A preview of the mobile sheet and its minimum/maximum possible configured totals.

Use existing staff permission and branch-scope checks. Validate bounds, unique option
identities, positive portions, valid unit/gram steps, prices, schedules and references.
Reject stale edits with a version conflict. Archiving preserves historical order data.
Do not publish meals with missing required choices, unavailable branch references or
unsupported inventory/tax mappings. Publishing needs no new public write endpoints.

## Backend and order integrity

This requires a first-class meal group rather than a frontend-only discount, a hidden
coupon or a temporary product created for every possible combination.

1. Store versioned branch meal definitions and reference actual component SKUs.
2. Represent cart meal instances independently: meal ID/version, selected option IDs,
   quantity and a stable client group identifier. A client never submits trusted prices.
3. Resolve definitions and compute component quantities, base/upgrade totals and taxes
   on the server. Batch related reads. Define deterministic per-component allocation
   and rounding so item tax, invoice totals and meal totals agree to the paisa.
4. Include the meal identity, selected choices, quantities, configuration revision and
   accepted price in the existing checkout quote/idempotency fingerprint. Both legacy
   and modern checkout must preserve or explicitly reject a meal, never drop it.
5. Aggregate real component demand across ordinary items and every meal. Apply service
   windows, availability, stock/production and pickup rules to those totals. Reserve all
   components and pickup capacity atomically; any failure rolls back the whole order.
6. Snapshot meal names, selected portions, allocated prices/tax and grouping for kitchen
   tickets, customer orders and invoices. Staff must see exactly what to prepare/pack.
7. Payment recovery and retries preserve the accepted snapshot. Cancellation/expiry
   releases the actual component holds once. Refunds use the stored paid amounts.
8. Reorder rebuilds against current definitions, prices and availability. Changed or
   archived meals require a clear review; never silently substitute a dish or charge
   ordinary dish prices for a formerly discounted meal.
9. Decide and enforce offer/reward stacking consistently in preview, acceptance and
   payment. A meal must not obtain an accidental second discount through another path.

Use bounded public reads, short versioned definition caching and explicit invalidation.
No extra idle polling or unbounded join of historical orders is needed. Track anonymous
builder opens, completed selections and successful meal purchases to evaluate demand.

## Acceptance tests

Cover valid required/optional groups, multiple selections, upgrades, multiple meal
instances and meals mixed with ordinary items; required/max violations; duplicate or
forged option IDs; cross-branch and stale definitions; schedules and closures; gram
rounding/portion rules; all tax combinations and discount/fee totals; quoted-price
changes; out-of-stock shared components; concurrent final-stock orders; transaction
rollback; retry/idempotency; payment recovery; holds on cancellation/expiry; refunds;
reorder of changed/archived meals; staff packing and invoice snapshots; permissions
and stale admin edits; bounded query counts; Hindi, larger text, reduced motion,
keyboard/focus restoration and mobile/desktop layouts. Verify Safari scrolling on a
real iPhone; simulated vibration calls cannot verify physical haptics.

## Current implementation and operational limits

Admin meal controls are in Menu workspace → App-exclusive meals. Branch-scoped menu
permission is required. Up to 32 versioned definitions per branch support NONE, FIXED
or PERCENT discounts, required/optional groups and paid upgrades. Internal cost estimates
are not returned to public clients. Publish only complete, valid branch recipes.

Customer selection adds real dish quantities atomically with versioned meal claims.
The cart shows a meal summary with Edit/Remove controls alongside aggregated dish rows.
Ordinary quantities of the same SKU remain independent of meal removal. Changing an
included dish so the recipe is no longer complete removes that deal and displays a
review-total notice. Server quotes, idempotency and persisted item allocations carry
meal identity; inventory/capacity/refund lifecycle still uses actual component items.

The first version supports pickup, ten customized selections per order and quantity
1–10 per selection. Each SKU appears once per recipe. Weight portions retain the SKU's
retail minimum/step and must have an exact paisa price, so combined weighted meals
cannot accumulate rounding errors. Meal deals do not stack with other offers or coin
redemptions. Existing net-spend earning rules still apply.

Reordering a historical meal directs the customer to rebuild against current choices
and prices. Archive/pause and stale versions require review. Costs/margins are owner-
entered estimates before tax, payment fees and overhead, not guaranteed profits.

## Automatic selected-dish pricing (October 30)

New meals default to `SELECTED_ITEMS`: sum the current branch selling price of
all selected portions, apply the configured fixed/percentage discount once per
meal (optionally capped by `maximumDiscount`), then multiply by meal quantity.
Percentage discounts round half-up to the paisa per meal. Taxes and checkout
charges are calculated later. The admin enters no base price or upgrades in this
mode; the stored base is zero and option upgrades must be zero. At least one
required dish remains mandatory, and discounts must leave the cheapest allowed
combination positive. A fixed rupee discount is per meal, not per order.

Existing definitions remain `FIXED`, preserving their base-plus-upgrade formula.
Changing the pricing mode or discount increments the existing version and makes
old claims require rebuilding. Current server branch prices remain authoritative;
client estimates never submit a price. Stock, tax allocation, pickup reservations,
non-stacking savings rules and staff component snapshots use the same paths.
Migration V145 adds the pricing mode and optional cap without repricing old meals.

Cards show the cheapest required combination as the starting price. The customer
builder displays the actual selected dish total crossed out and the discounted
meal total on the shared yellow price treatment. Selected portion prices and the
Add total update on each change; no artificial comparison price is invented.

If the server meal quote differs from the displayed total or regular dish total,
the builder remains open and leaves the cart untouched. Show the previous and
updated prices and a Confirm price action. Confirmation obtains a fresh quote;
a further change requires another review. Consent is scoped to the recipe,
choices, quantity, selected catalog prices, branch, cart and pickup snapshots,
and is discarded when the builder closes or its choices change.
