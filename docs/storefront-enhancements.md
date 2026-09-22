# Storefront and ordering enhancements

## Safety baseline and rollout

All new feature switches ship **OFF** in `backend/src/main/resources/application.properties`.
Existing `inventory.enforcement-enabled` and `inventory.automation.scheduler-enabled` values
are deliberately unchanged. Do not disable either existing mechanism as an enhancement rollback.

| Property | Default | Effect when enabled |
| --- | --- | --- |
| `gokul.features.smart-availability` | `false` | Cart availability API and additional authoritative branch-window/expected-ready rules |
| `gokul.features.smart-pickup-selection` | `false` | New pickup selector; requires smart availability as well |
| `gokul.features.inventory-automation-v2` | `false` | Bounds existing generation to the rolling horizon and refreshes existing stock under checkout-compatible locks |
| `gokul.features.customer-home-v2` | `false` | Visual interactive homepage, branch-specific menu and optional merchandising |
| `gokul.features.homepage-campaigns` | `false` | Publishes scheduled campaigns; homepage rendering also requires customer-home-v2 |
| `gokul.features.future-ordering-days` | `30` | Global inclusive advance-date ceiling, between 1 and 60 days; does not expand shorter branch/product/rule limits |

Use the existing Spring configuration/environment override mechanism. For example,
`GOKUL_FEATURES_SMART_AVAILABILITY=true` enables only the first switch. Flags are
deployment configuration, not an unauthenticated admin toggle. Configuration changes
require a backend restart unless the hosting platform already handles it.

`GET /api/storefront/features` exposes only these customer-safe settings and the business
date. Clients coalesce flag requests and refresh every 60 seconds. During initial loading
they show a loading state, not a flash of the legacy selector. The last successful settings
are retained in tab session storage for up to 15 minutes during an outage, with a visible
retry message on pickup. An unknown/expired configuration shows an explicit error; it
does not imply OFF. The customer can deliberately choose standard pickup as a fallback.
A successful OFF response restores the legacy layout. No second set of frontend feature flags
exists. Frontend customer requests use the existing `NEXT_PUBLIC_API_URL`; admin requests
use the existing `NEXT_PUBLIC_API_BASE_URL`. Configure both to the same backend in DEV.

Recommended rollout: review real branch/product policies and pickup settings; run the
regressions below in DEV; enable smart availability first, then selection; review guaranteed
production before enabling automation v2; enable the new homepage, then campaigns. This
change does not enable any production flag, deploy, call a courier, or change existing stock.

## Stage report and file map

Java paths below are relative to `backend/src/main/java/com/gokulsweets/restaurant/`.
Frontend paths are relative to `frontend/`. These stages are implemented in one isolated
worktree; the table is not a claim that a production rollout has occurred.

| Stage | Changed implementation files | APIs / migrations | Flags and rollback |
| --- | --- | --- | --- |
| 1. Admin order detail | `types/adminOrders.ts`, `app/admin/orders/page.tsx` | Existing order-detail response already contains saleMode, quantity, weightGrams, unitPrice, taxRate, taxAmount and lineTotal. No API or migration change. | Presentation-only, always available. Revert the two UI changes to restore the old presentation; order data and tax snapshots are untouched. |
| 2. Tax administration | `tax/AdminTaxCategoryController.java`; `app/admin/tax-categories/page.tsx`, `services/adminManagementApi.ts` | Additive admin list/create/update/status APIs. No migration. | Admin-only, `MENU_MANAGE`. No customer tax algorithm change. Remove navigation/route to hide; flags do not undo intentional tax edits. |
| 3. Pickup scheduling | `pickup/PickupSlotController.java`, `pickup/repository/PickupSlotRepository.java`; `app/admin/pickup-scheduling/page.tsx` | Existing bulk POST reused; additive admin date-range GET. No migration. | Admin-only, `BRANCH_MANAGE` plus assigned branch. Generated slots remain on rollback; never delete booked slots. Authorization improvements are intentionally not switchable. |
| 4. Availability and final authority | `config/EnhancementProperties.java`, `config/StorefrontFeaturesController.java`, `order/controller/CartAvailabilityController.java`, `order/service/CartAvailabilityService.java`, `order/service/SmartOrderingRules.java`, `order/service/OrderValidationService.java`, `order/service/OrderService.java`, `inventory/service/OrderInventoryReservationService.java`, `inventory/repository/InventoryDailyAllocationRepository.java`, `common/security/SecurityConfig.java`, application properties | New cart availability and public flags APIs. No migration. | smart-availability OFF returns 404 from the optional API and bypasses only the new window/expected-ready rules. Original locks, stock checks, capacity reservations and payment commitments remain. |
| 5. Smart dates/alternatives | `hooks/useStorefrontFeatures.ts`, `services/availabilityApi.ts`, `components/checkout/SmartPickupSelection.tsx`, `app/checkout/pickup/page.tsx` | Uses stage 4 and existing checkout storage. | Both availability and selection flags required. OFF restores the original selector. No cart or customer storage migration. |
| 6. Rolling inventory | `inventory/automation/service/InventoryAutomationGenerationService.java`, `inventory/automation/scheduler/InventoryAutomationScheduler.java`; `app/admin/inventory/{setup,automation}/page.tsx`, `lib/inventoryHelp.ts` | Existing automation/policy APIs, schema, audit runs and ledger reused. No migration. | inventory-automation-v2 OFF restores the previous scheduling/range behavior. Generated allocations and their reservations persist, intentionally. |
| 7. Interactive storefront | `menu/StorefrontHighlightsController.java`; `app/page.tsx`, `app/cart/page.tsx`, `components/menu/{CustomerHomeExperience,MenuScreen,PickupContext,ProductGrid,ProductCard,WeightSelectorSheet}.tsx`, home CSS module, `hooks/usePickupIntent.ts`, `components/layout/AppShell.tsx` | Additive branch highlights GET; existing menu/cart/weight-selector/branch APIs reused. No migration. | customer-home-v2 OFF renders the existing homepage. Early menu/cart date checks depend only on smart-availability. Reusable weight-dialog keyboard focus containment remains as an accessibility correction. |
| 8. Campaigns | `campaign/{HomepageCampaign,HomepageCampaignRepository,CampaignRequest,CampaignService,CampaignController}.java`, `storage/R2StorageService.java`, `exception/GlobalExceptionHandler.java`; `app/admin/homepage-campaigns/page.tsx`, `types/campaign.ts`, `lib/campaigns.ts`, `components/menu/CampaignMedia.tsx` | `V51__create_homepage_campaigns.sql` and `V52__campaign_retry_keys.sql`; additive public/admin campaign/media APIs and optional idempotency headers. | homepage-campaigns OFF serves an empty public list and restores default homepage content. Keep both migrations/tables and R2 assets; no destructive downgrade needed. |

Shared admin wiring: `components/admin/{AdminSidebar,AdminMobileNav,BranchOperationalSettings}.tsx`.
The existing SettingField/SettingToggle help pattern is reused; new fields have labels,
operation descriptions and accessible label associations. `backend/build.gradle` adds the
independent `enhancementTest` source set and includes it in `check`; existing tests are not
removed or excluded.

Revision-only administration also adds `components/admin/AdminNavigation.tsx`, shared
permission-filtered desktop/mobile groups, and a working Offers page at the existing
`app/admin/notifications/page.tsx` URL. Offers use the existing rebate endpoints, not a new
discount engine. `app/admin/menu/images/page.tsx` adds Back to menu and retains branch,
search, category, image filter and selected product in tab storage. The menu's forward
image-management link remains. These admin navigation/UI corrections are not feature-gated.

Desktop and mobile use the same groups, open the current group automatically, and omit
empty groups for restricted staff. Mobile navigation supports focus containment, Escape
and return focus. All pre-existing destination URLs remain:

| Group | Routes | Existing permission checks |
| --- | --- | --- |
| Direct links | `/admin`, `/admin/reports`, `/admin/me` | Dashboard/account as before; `REPORT_VIEW` for reports |
| Orders & customers | `/admin/orders`, `/admin/printing`, `/admin/customers` | Existing order view/action permissions; `ORDER_VIEW` for printing; `CUSTOMER_VIEW` |
| Menu | `/admin/menu`, `/admin/menu/live`, `/admin/menu/images`, `/admin/menu/import`, `/admin/tax-categories` | `MENU_MANAGE` |
| Storefront | `/admin/homepage-campaigns`, `/admin/notifications` | `MENU_MANAGE` for campaigns; Offers now uses existing `REBATE_VIEW`/`REBATE_MANAGE` |
| Branch operations | `/admin/branches`, `/admin/pickup-scheduling` | `BRANCH_MANAGE` and backend branch access |
| Inventory | `/admin/inventory`, `/admin/inventory/setup`, `/admin/inventory/automation`, `/admin/inventory/production` | `INVENTORY_VIEW`/`INVENTORY_MANAGE` plus backend action/branch rules |
| Team | `/admin/staff`, `/admin/approvals`, `/admin/payroll` | `STAFF_MANAGE`, `APPROVAL_VIEW`, `PAYROLL_VIEW` respectively |

The previous owner-only **navigation placeholder** for Offers & notifications is the
intentional exception: it now exposes the real offer page to staff who already have
rebate permissions. Backend authorization is unchanged, including owner-only global
offers. Inventory is now accessible from mobile navigation as well as desktop.

## API contracts

### Tax categories

`GET/POST /api/admin/tax-categories`, `PUT /api/admin/tax-categories/{id}`,
`PATCH /api/admin/tax-categories/{id}/active` (`{"active":false}`).

Create/update fields: `code`, `name`, `hsnSacCode`, `cgstRate`, `sgstRate`, `igstRate`,
`active`. Existing snake_case database fields are unchanged. Codes are normalized to
uppercase and unique. Rates are 0..100 with at most two decimal places; CGST+SGST cannot
exceed 100. **Pickup continues to use CGST+SGST, not IGST.** Saved order tax amounts are
never recalculated. Existing order validation rejects products assigned to inactive tax
categories; reassign such products before deactivation. There is no tax deletion endpoint.

### Pickup scheduling

Existing `POST /api/admin/branches/{branchId}/pickup-slots` retains:

```json
{
  "startDate": "2026-10-01", "endDate": "2026-10-03",
  "startTime": "09:00", "endTime": "21:00",
  "slotDurationMinutes": 30, "capacity": 10,
  "priorityEnabled": true, "priorityCapacity": 2, "priorityCharge": 20
}
```

Existing generator validation/divisibility, skipping of past/overlapping slots, and
normal/priority counters are unchanged. Admin GET on the same path accepts `startDate`
and `endDate` (inclusive, at most 61 days) and includes full and inactive slots. Existing
update/delete endpoints now also check the slot's branch authorization. The customer GET
contract is unchanged. Hours use the existing India business-time convention.

### Cart availability

`POST /api/branches/{branchId}/availability`:

```json
{
  "startDate": "2026-10-01", "days": 7, "fulfilmentType": "PICKUP",
  "items": [
    {"productId": 101, "quantity": 10},
    {"productId": 102, "weightGrams": 250}
  ]
}
```

`days` is 1..61 and items are limited to 100 per request. The returned range is clamped
to the rolling horizon. Response:

```json
{
  "fulfilmentType": "PICKUP", "today": "2026-09-22", "maximumDate": "2026-10-22",
  "dates": [{
    "date": "2026-10-01", "available": true,
    "slots": [{
      "slot": {"id": 99, "...": "unchanged PickupSlotResponse fields"},
      "normalAvailable": true, "priorityAvailable": false, "reason": null
    }]
  }]
}
```

Every date is represented, including dates with no slots; `available` means some pickup
mode can fulfil the **whole cart**. The full list supplies directly selectable alternatives,
not merely an advisory next date. Invalid products/weights produce a real 4xx response.
Unavailable stock/times produce unavailable entries with business-friendly reasons.
Failed requests are never converted into `available:true`.

Additive date fields `items` and `reason`, and slot fields `code` and `issues`, explain
individual blockers without stopping at the first product. Each item includes
`productId`, `productName`, `unit` (PIECE/GRAM), normalized `requestedQuantity`,
nullable `availableQuantity`, `available`, `code`, `reason`, and `expectedReadyAt`.
Codes distinguish `ONLINE_DISABLED`, `PRODUCT_HORIZON`, `NO_ALLOCATION`,
`POLICY_MISMATCH`, `AWAITING_APPROVAL`, `DELAYED`, `BOOKING_CLOSED`,
`READY_STOCK_REQUIRED`, `SOLD_OUT`, `QUANTITY_TOO_LARGE`, `NOT_READY`,
`PREPARATION_TIME`, `NO_SLOTS`, `SLOT_FULL` and slot `PICKUP_WINDOW`.
Dates are clamped to the shortest relevant global/branch/product horizon. A specifically
requested date outside a product policy still returns one explained unavailable date.
An individually feasible product does not imply the entire cart has a shared time.
Stock is calculated once per item/date from batched policy, allocation and slot reads;
there are no allocation queries per slot. Final transactional checks are not cached.

Cart normalization, product state, minimum/increment weights, branch prices and active
tax validation reuse OrderValidationService. Availability uses existing daily stock
and InventoryAvailabilityService: approved/ready ceiling minus safety buffer, holds,
committed quantity and wastage. It checks online policy, product horizon, production lead
and expected-ready time, active/future slots, normal/priority capacity, enabled branch
pickup and the shorter branch/global advance window. Existing opening/closing hours are
generation defaults; changing them does not silently reinterpret already-created slots.
The existing inventory-enforcement switch still controls stock enforcement.

The endpoint is read-only: no forecast becomes stock, no reservations are created, and no
customer identity or order's private hold is accepted to inflate availability.
Existing pending orders therefore use the original reservation-aware editor rather than
an anonymous preview of their own reserved stock.

Final create/update still runs inside OrderService's transaction. Stock rows use the
existing pessimistic lock in date/product order; pickup counts use the existing atomic
capacity-limited UPDATE. A failure rolls back order/items/capacity/holds/ledger together.
With smart availability ON, final validation additionally enforces branch/global windows
and expected-ready time. Moving an owned hold to an earlier slot on the same day is
revalidated even though its quantities can be reused. Unchanged owned reservations retain
their original semantics; payment converts owned holds to commitments, rather than
subtracting the customer's own hold twice or performing a second anonymous preview.
These changes do not replace payment verification, expiry, idempotency or compensation.

### Optional merchandising

`GET /api/branches/{branchId}/storefront-highlights` returns `trendingProductIds` and
`newProductIds`. Ranking uses existing `analytics_product_daily.order_count` over the last
30 business dates (never mixed piece/gram quantities). New products use actual product
creation timestamps from the last 30 days. Candidates must belong to the live branch menu
and have a feasible minimum-quantity pickup within the horizon. The API returns at most
four of each; the landing page shows at most three unique highlights. No history means no
fabricated trend; an optional failure omits the sections while the menu link remains.
Exact customer quantities are checked at pickup/checkout.
Optional highlights are cached per application instance for 30 seconds, keyed by branch,
business date and ordering horizon, with at most 128 entries. Concurrent requests share
the same cached refresh; cart and checkout availability checks remain uncached.
No product photos, trend data or special products are synthesized.

### Campaigns and R2

- `GET /api/storefront/campaigns`: active campaigns only, ordered by displayOrder then ID.
- `GET/POST /api/admin/homepage-campaigns`; `PUT /api/admin/homepage-campaigns/{id}`.
- `POST /api/admin/homepage-campaigns/{id}/media?fallback=false`: multipart `file`, upload/replace.
- `DELETE` on that media path removes the main media; `fallback=true` manages its static image.

All admin operations require `MENU_MANAGE`. V51 stores `type` (HERO/FEATURE), title,
subtitle, media URL/MIME, fallback URL, CTA label/target, nullable start/end instants,
active, display order, and timestamps. Drafts can omit media; activation requires it.
Animation additionally requires a static fallback. First version is global; future
branch targeting can add a nullable branch relation/filter here without replacing the model.

Admin schedule inputs are explicitly **Asia/Kolkata**, converted to UTC instants; DB
columns are timezone-aware. Start is inclusive and end is exclusive. Null means no
corresponding boundary; end must be after start. The public endpoint does no caching;
the homepage refreshes every 30 seconds and removes expired campaigns on its one-second
clock. Next eligible priority wins, then real product/default content. A broken animated
source uses its static fallback; a broken fallback skips to the next active campaign.
Feature API or campaign API outages never remove the ordering CTAs.

CTA destinations are restricted to `/menu`, `/cart`, `/about`; arbitrary external URLs,
JavaScript URLs and HTML cannot be configured. Media uses the existing R2 S3 client,
bucket and public base URL under `campaigns/{id}/{uuid}.{extension}`. Allowed main media:
JPG/PNG/static WebP/GIF/MP4/WebM, **5 MB maximum**, checked by declared MIME and file
signature. Animated PNG/WebP are rejected to keep static fallback promises meaningful.
Fallback accepts static JPG/PNG/WebP only. Multipart overflow returns explicit 413.
Video should be short and silent; rendering is muted, looping, inline with native controls.
Below-fold animation waits for viewport proximity, images are responsive/lazy, and
reduced-motion uses the static image without autoplay.

Object-storage writes cannot share a SQL transaction. Unique object keys prevent stale
cached replacement content. Old media is deleted only after DB commit; failed DB work
cleans up the newly uploaded object. Campaign deletion helpers reject product/outside
asset URLs. Removing required media deactivates the campaign. Cleanup failures are
logged with the orphan URL for operational retry; they do not roll back an already
committed replacement. No second storage system or external upload service is added.

The initial admin form includes main/fallback file selection, a local preview, file
type/5 MB guidance, **Save draft** and **Publish campaign**. Saving a draft unpublishes
an active campaign. Explicit save/publish runs inactive metadata -> changed fallback ->
changed main -> activation (only for publish). Successful steps, selected files and the
persisted ID are retained after failure; metadata-only saves never re-upload unchanged files.
In-flight guards reject duplicate submissions before React's next render.

V52 adds nullable `creation_request_id` (unique), `creation_request_hash`,
`media_request_id`, `fallback_request_id` to `homepage_campaigns`, plus
`campaign_media_requests` keyed by `(campaign_id, fallback, request_id)` with a payload
hash and campaign FK. Optional UUID `Idempotency-Key` headers protect create and upload
retries. Create uses a canonical metadata fingerprint and database conflict/row locking;
upload fingerprints MIME/content, locks the campaign and records the receipt in the
transaction. Different payload/key reuse is rejected. Replaying replaced/removed media
is rejected, never restored over newer artwork. Legacy clients without headers still work.
If a create response is lost and the user edits fields before retrying, the payload
conflict is explicit: reopen the saved draft rather than silently creating another one.

Local file preview, typing, rendering, metadata updates and successful receipt replays
perform no R2 PUT. Admin rendering requires no R2 LIST/HEAD. **Necessary explicit PUTs
still incur R2 Class A operations**; replacements/removals also require cleanup operations.
SQL and object storage cannot provide exactly-once writes across a process crash after
PUT but before database commit; an ambiguous failed attempt can require another PUT or
orphan cleanup. Retry receipts are not a zero-cost or distributed-transaction guarantee.
All upload/replace/remove verification here uses fake/mock S3, not live credentials.

### Admin offers

`/admin/notifications` now contains offer list/create/edit/activate/deactivate using
existing `GET/POST /api/admin/rebates`, `PUT /api/admin/rebates/{id}` and
`PATCH /api/admin/rebates/{id}/{activate|deactivate}`. It supports percentage (with
required discount cap), fixed amount and spend slabs, optional minimum spend/usage
limits, public/code-only visibility, and general/customer-phone targeting.
Schedules are local **Asia/Kolkata** date-times as expected by the existing API;
they are not converted using the admin device timezone. New offers are enabled but
only eligible within their schedule; edits preserve activation status.

`REBATE_VIEW` and `REBATE_MANAGE`, existing branch access, and owner-only global-offer
rules remain authoritative. The branch chooser uses active public branch names filtered
by staff access, so rebate-only staff do not need unrelated branch-management permission.
No migration, extra enhancement flag, notification sender, discount calculation, tax or
payment change is introduced. Customer checkout still discovers/applies/removes rebates
through the existing order endpoints. Turning enhancement flags OFF does not undo
intentional offer creation; deactivate unwanted offers through this existing API.

## Inventory policy meaning

The illustrative "daily capacity 20 kg / online reserve 5 kg" is **not** a new default or
authorization to repurpose stock. Existing fields retain distinct meanings:

| Business label | Existing field | Meaning |
| --- | --- | --- |
| Daily production capacity | `maximum_daily_allocation` | Upper bound the branch can reliably promise online per service date |
| Guaranteed online quantity | `inventory_automation_rules.guaranteed_quantity` | Deliberately approved online quota before the buffer; never an inferred forecast |
| Safety buffer | `default_safety_buffer` / per-date buffer | Stock withheld from online sale |
| Preparation time | `production_lead_minutes` | Minimum time before pickup |
| Future ordering window | `booking_horizon_days` | Product's advance-booking limit |
| Automatic planning window | `generation_horizon_days` | Rule's generation limit, capped by product/global horizons |
| Online ordering enabled | `online_enabled` | Existing branch-product opt-in |

Existing seasonal/day masks, manual-only rules, suggest-only/draft/guaranteed modes and
ready-stock restrictions remain. The existing scheduler generates a bounded rolling
range; no manual daily setup is necessary for explicitly configured guaranteed rules.
Daily rows are retained because the existing transactional reservation ledger requires
them; no unbounded future rows or duplicate virtual-inventory engine is introduced.
Existing manually managed or stock-active rows are not overwritten. V2 refreshes existing
rows under the same date/product lock order as checkout before considering any updates.

#### Operational readiness workflow and diagnosed causes

A configured policy is **not** an approved allocation, and an approved guaranteed
allocation is **not** ready stock. The screenshots cannot establish the exact cause
in the user's database. Code tracing did establish these behaviors:

- `AUTO_APPROVE_GUARANTEED` creates APPROVED quantity, not READY stock.
  A ready-stock-required product still needs staff to record actual ready quantity/status.
  Never mark future stock READY just to make the selector green.
- Preparation time is measured from current India business time; a date can legitimately
  have unavailable morning slots and feasible later slots. An explicit `expected_ready_at`
  also constrains pickup. Automated generation does not write a perpetually moving
  readiness timestamp or override manually entered readiness.
- Policies, product/rule horizons, seasonal/day masks, draft/guaranteed mode, generated
  daily rows and pickup slots all need to cover the requested service date. Existing
  manual allocations and rows with holds/commitments/actual stock remain protected.
- The old first-failure response hid these distinctions. The new per-item reasons
  and compact all-cart selector explain them without approving speculative stock.

Admin Inventory now links the sequence **Product policies -> Future production ->
Daily availability / Production & ready stock -> Pickup scheduling**. Rule rows display
ready-stock requirement, preparation time and booking window. Expand a generation run
to load aggregated product/outcome/reason/date-range/count explanations using
`GET /api/admin/inventory/branches/{branchId}/automation/runs/{runId}/explanation`
(existing INVENTORY_VIEW and branch authorization). A V2-bounded generation records a
SKIPPED explanation for requested dates beyond its rule/product/global horizon.
V2 OFF leaves the existing scheduler's range/production configuration unchanged.

## Customer journey and delivery extension seam

The new homepage is an original image-led landing page, not a duplicated shopping grid.
It uses configured campaign/product media, editorial typography, category links and at
most three compact highlights. Missing/broken product photography uses another real
configured photo or a designed text fallback; no food photos or stock are fabricated.
Order Now / Explore Menu lead to the dedicated menu, which reuses ProductGrid, piece
controls, the validated grams/kg selector and the mobile floating cart summary.
Touch controls on the date-aware menu are at least 44px. Choosing another branch does not erase
the old cart; customers are explicitly directed to review it before mixing branches.
Optional trend/campaign failures do not block menu browsing. The enhanced home and pickup
screen suppress the existing social-follow popup to avoid interrupting ordering.

The journey is **branch and products -> pickup time -> customer details -> review/payment**.
Menu and cart share a branch-specific pickup-date preference using existing storage
events and the saved pickup slot. Browse-first says "Choose pickup to check availability".
Menu checks at most 100 displayed products (cart quantities or minimums) for one date,
debounced 350 ms. Cart checks its actual items and identifies exact blocked products,
with quantity editing/removal under customer control and an all-cart alternatives link.
Unchosen menu products cannot invalidate the actual cart's saved pickup.

Smart pickup uses a date strip and a native **Pickup time dropdown**, with available
normal/priority choices, exact priority surcharge, selected summary, next-all-cart action,
and collapsed unavailable-time reasons. Full-horizon checks run on cart/branch changes
and every 60 seconds, debounced 250 ms; clicking date/time/mode reuses that result rather
than recomputing the horizon. Menu/cart checks do not poll. Abort controllers and request
keys discard stale responses; the Continue check also rejects
a response if the cart/selection changed while it was in flight. Network errors offer
Retry or the original selector, clearly stating that availability has not been confirmed.
No availability path clears cart or customer storage, removes/substitutes products or
splits orders/payments. A selection is only a preview until
the existing final transactional checkout reserves it.

**Delivery is not enabled.** Existing orders/requests still mean pickup and require no
delivery address. `PickupType.NORMAL/PRIORITY/ADMIN_OVERRIDE` is a pickup service level,
not a fulfilment type; do not overload it to represent delivery. The new availability
contract accepts only optional `"PICKUP"`; omitted means pickup and `"DELIVERY"` is rejected.
No speculative order schema or courier framework has been added.

For a future delivery project:

1. Add a backwards-compatible order fulfilment type defaulting existing rows/requests to
   PICKUP; snapshot validated delivery address/contact only for DELIVERY.
2. Resolve serviceability/zones and branch routing before shared cart/stock validation.
   Keep zone, distance, dispatch and address rules out of InventoryAvailabilityService.
3. Introduce delivery windows as a separate fulfilment policy; map their service date/time
   into the existing shared product/stock reservation transaction. Do not reuse pickup
   capacity as courier capacity.
4. Snapshot delivery charges and their approved tax treatment in authoritative order
   calculation. Reuse payment, idempotency, inventory holds/commitments and compensation;
   do not create a second charging or inventory flow.
5. Map delivery dispatch/provider statuses through an explicit lifecycle adapter with
   idempotent callbacks, cancellation/refund handling and reconciliation. Preserve the
   current pickup/KOT lifecycle and integrations for pickup orders.

Coverage, zones, pricing, tax treatment, promises and provider choice require explicit
business approval in that future project.

## Verification and operational limits

Original stage verification, plus revised worktree verification (2026-09-23, isolated
synthetic data; no user's existing database was modified):

| Stage | Verified evidence | Remaining rollout work |
| --- | --- | --- |
| 1. Order detail | Frontend typecheck/build/lint; focused money test and real API assertions for 10 pieces and 250 g, saved tax/line totals | Admin business acceptance of displayed quantities on representative historical orders |
| 2. Tax | Real authenticated create/edit/deactivate and anonymous rejection; frontend checks | Review category assignments before deactivating any real category |
| 3. Pickup | Branch-permission unit test; real cross-branch list/generate rejection and repeat-safe generation; frontend checks | Approve actual branch schedules/capacities |
| 4. Availability | Shared-rule/default/independent-flag tests, preview alternatives test; real OFF/ON window/readiness checks and PostgreSQL 12-way stock/8-way slot races | Controlled production-like load and business-policy acceptance; legacy test suite remains blocked below |
| 5. Smart selection | Browser checks for alternatives, stale responses, retained cart/customer input, failed-check fallback and flag-OFF original selector | User acceptance on actual customer devices |
| 6. Automation | Scheduler OFF/ON test; real generation plus SQL verified 31 dates at 500 g approved/100 g buffer, not the fixture's uncapped 1000 g | Explicit review of real guaranteed production rules before enabling |
| 7. Storefront | Mobile browser quick-add, weight-price flow, keyboard focus, layout, flag-OFF; final production build/typecheck/full lint | Real menu imagery/content review; no invented assets added |
| 8. Campaigns | Service/storage tests; real CTA, activation, invalid signature and 413 size rejection; two Node schedule/timezone tests and browser reduced-motion/next/default/error fallback | Actual DEV R2 upload/replace/remove smoke test with authorized credentials |

The original run had 19 focused tests across seven classes. The final revised run of
`REVISION_DB_TEST=true ./gradlew enhancementTest bootJar --quiet`, with the disposable
database/R2 dummy environment described below, passed **30 tests across nine classes,
zero failures/errors/skips**. This is independent `enhancementTest` coverage, **not** a
passing standard backend `test` or `check` task. Frontend `npx tsc --noEmit`, full ESLint,
two Node schedule/timezone tests and `npm run build` passed (46 static pages).
All three browser suites passed against the isolated current packaged backend.
No live R2 or payment integration success is implied.

| Revised scope | Final evidence | Remaining acceptance gap |
| --- | --- | --- |
| Campaign creation/retry | Browser initial local preview with zero uploads; double click = one draft/one upload; failed publication retry and metadata update add zero uploads. Real PostgreSQL + mocked S3: eight concurrent creates yield one row; eight identical uploads yield one successful PUT; failed PUT retry, payload conflict and scoped/replaced receipts checked. Existing replacement/removal cleanup tests retained. | No live R2 upload, replacement or removal with credentials; crash-between-PUT-and-commit is not exactly-once |
| Inventory/pickup | `revision_http.py` configures policies, rules, manual readiness and slots through real admin APIs before reading the real menu. Mixed piece+gram/category carts, draft/ready/manual states, expected-ready time, product horizon, no-slot reason, future alternatives, preserved manual/held rows and 100 g buffer verified. Eight concurrent 500 g orders against 900 g net stock accept exactly one. Independent unit tests cover India midnight/preparation boundaries and batching. | Exact cause in the user's local DB remains unknown; review sanitized policy/allocation/slot responses if still reproducible |
| Customer journey | Native dropdown, quantity edit from 950 g to 250 g, named partial-cart blocker, unchanged two-item cart, initial slow feature loading, known-ON outage without legacy swap, explicit fallback, stale request rejection and no horizon request on time selection. Viewport/full-page screenshots inspected at 390 px and 1440 px. | Physical-device/user acceptance and real configured food photography; synthetic labelled media tests geometry, not food-photo quality |
| Admin navigation/images | Owner desktop and restricted-role mobile checks, active groups, hidden unauthorized groups, Escape behavior; image Back to menu and restored search state | Additional organization-specific role combinations should be checked during rollout |
| Offers | Direct user request in this session asked to integrate offer creation. Real existing rebate API verified for percentage/fixed/slab creation, edit retaining disabled state, activation, branch/global permission rejection, customer/schedule/code-only eligibility and apply/remove on a real synthetic order | No notifications integration added; no live payment/redemption provider test |

Run:

```sh
cd backend
./gradlew enhancementTest
./gradlew compileJava bootJar
cd ../frontend
npx tsc --noEmit
npm run lint -- --quiet
node --test tests/campaigns.test.mjs
npm run build
```

The independent enhancement suite covers flag defaults/dependencies/OFF behavior,
availability alternatives and held grams, exact piece/weight tax examples, window/prep
boundaries, branch authorization, unchanged scheduler range when OFF, campaign scheduling,
activation, replacement commit/rollback cleanup, media types/size and deletion isolation.
The normal `./gradlew test` is **not reported as passing**: its pre-existing
`RazorpayClientSignatureTest.java:3` imports `com.fasterxml.jackson.databind.ObjectMapper`,
which is absent on this Spring Boot 4/Jackson 3 classpath. No unrelated test source was
changed, and the new suite is not an exclusion-based claim about the old suite.

Isolated end-to-end fixtures and checks live in `backend/src/enhancementTest/`.
Only use `resources/seed.sql` in a freshly migrated **local disposable**
`gokul_enhancements` database; its guard rejects other database names. Run two local
backend instances against that fixture DB: :18309 with all new flags ON, :18310 with
default OFF. Override the existing automation scheduler OFF **only on those test
instances**, and use dummy R2 credentials without invoking valid uploads/payment.
`python3 backend/src/enhancementTest/http_regression.py` checks real API permissions,
tax CRUD, repeat-safe slots, flag-OFF behavior, expected-ready/horizon rejection, and
concurrent checkout against real PostgreSQL:

- 12 simultaneous requests for 250 g against 500 g: exactly two accepted.
- Eight simultaneous requests against one pickup-capacity place: exactly one accepted.
- Same-day held-stock transfer before readiness rejected; nearby available dates remain.
- Guaranteed automation capped a 1000 g rule at its 500 g policy maximum, retaining a
  100 g safety buffer, and generated 31 inclusive dates for a 30-day horizon.

Browser scripts `frontend/tests/{storefront,campaigns}.browser.mjs` use Playwright against
the isolated :3309 DEV frontend. Set both existing frontend API URL variables to :18309.
Use an installed Playwright module or set `PLAYWRIGHT_MODULE` to its absolute module path.
These checks cover mobile quick-add/weight controls, keyboard focus containment,
cart/customer preservation, directly selectable alternatives, stale responses, explicit
network-failure fallback, flag-OFF rendering, campaign scheduling, reduced motion and
broken-media/API fallback. Campaign media in those browser tests is explicitly synthetic.

For the revision regression use only `resources/revision_seed.sql` in the named disposable
`gokul_enhancements_revision` PostgreSQL database on `127.0.0.1:55489` (guarded by database
name). It inserts catalogue/auth fixtures only; `revision_http.py` performs actual admin
inventory/rule/generation/slot setup. Run backend :18409 with all new flags ON and :18410
with defaults OFF; disable the existing scheduler only on these test instances.
The seed/script requires a fresh disposable database and must not be run against user data.
`CampaignConcurrencyTest` additionally requires `REVISION_DB_TEST=true` and that same
isolated database; without the opt-in it is skipped, not covered by the default focused run.
The S3 client in that class is a mock. Use dummy R2 environment values, never real
credentials for these tests.

Revision browser command:

```sh
PLAYWRIGHT_MODULE=/absolute/path/to/playwright \
REVISION_ARTIFACTS=/absolute/path/to/session/artifacts \
node frontend/tests/revision.browser.mjs
```

It targets the task-only :3409 frontend with both API URL variables set to :18409.
The original two browser suites also support `BROWSER_BASE`, `BROWSER_API`,
`BROWSER_BRANCH=2001` and `BROWSER_PRODUCT='Revision Kaju Katli'` for that revision fixture.
Logs/screenshots remain session artifacts rather than production/public assets.

All Flyway migrations through **V52** and Hibernate schema validation were exercised on
isolated PostgreSQL 17. The exact additive migrations are
`V51__create_homepage_campaigns.sql` and `V52__campaign_retry_keys.sql`.
No real payment, courier or live R2 upload was performed. Real
DEV-bucket credentials, media quality/size review, actual branch policies and controlled
business acceptance remain rollout prerequisites. Feature OFF needs no DB rollback,
but it cannot reverse intentional admin tax changes, created slots/allocations, existing
orders/reservations, uploaded assets, offers, or improvements to admin authorization.
Do not delete either migration's tables/columns as a flag rollback or modify applied
Flyway checksums. Older application code can ignore the additive V52 fields/table.

### Exact rollout prerequisites

1. Apply the normal Flyway migrations including V51/V52 with the existing database user,
   deploy backend/frontend together, and configure both frontend API base variables.
   This worktree does not perform any of those deployment actions.
2. Keep the existing production values `inventory.enforcement-enabled=true` and
   `inventory.automation.scheduler-enabled=true` unchanged. New defaults remain
   `gokul.features.{smart-availability,smart-pickup-selection,inventory-automation-v2,customer-home-v2,homepage-campaigns}=false`.
   `gokul.features.future-ordering-days=30` is an inclusive ceiling, not stock approval.
3. For early menu/cart checks and authoritative new prep/window rules enable
   `GOKUL_FEATURES_SMART_AVAILABILITY=true`. For the dropdown also enable
   `GOKUL_FEATURES_SMART_PICKUP_SELECTION=true`; selection alone is exposed as OFF by
   the backend. Neither flag requires the enhanced home or V2 automation.
4. Explicitly verify each branch's pickup settings/slots, policy horizon, ready-stock
   requirement, guarantee, buffer and generated allocations before enabling
   `GOKUL_FEATURES_INVENTORY_AUTOMATION_V2=true`. It is independently gated and does
   not convert forecasts/drafts to approved or ready stock.
5. Enable `GOKUL_FEATURES_CUSTOMER_HOME_V2=true` for the new landing page. Review real
   menu photography first for the image-led result. Enable
   `GOKUL_FEATURES_HOMEPAGE_CAMPAIGNS=true` only when campaigns are ready; both home
   and campaign flags are needed to render them on the enhanced homepage.
6. Authorize a separate DEV-bucket smoke test using existing `R2_ACCOUNT_ID`,
   `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `cloudflare.r2.bucket-name` and
   `cloudflare.r2.public-url` configuration before claiming real media operations work.
   No live credentials were used here. Assign existing admin permissions rather than
   bypassing authorization. Recheck the normal backend suite once its unrelated
   Jackson test-source mismatch is repaired in its own scope.

These revisions were developed and verified on
`iamvivek907-storefront-and-ordering-enhancements` in
`/Users/macbookairm1/.copilot/repos/copilot-worktrees/gokul-sweets/iamvivek907-fictional-engine`.
Publishing the branch does not merge or deploy it. Task-owned :3409/:18409/:18410
servers and :55489 PostgreSQL are stopped after verification; evidence and code remain.
