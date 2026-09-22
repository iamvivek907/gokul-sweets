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
date. Clients refresh flags every 60 seconds. Configuration failure falls back to the
original homepage/selector, with a logged warning. No second set of frontend feature flags
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
| 7. Interactive storefront | `menu/StorefrontHighlightsController.java`; `app/page.tsx`, `components/menu/CustomerHomeExperience.tsx`, its CSS module, `components/menu/ProductCard.tsx`, `components/menu/WeightSelectorSheet.tsx`, `components/layout/AppShell.tsx` | Additive branch highlights GET; existing menu/cart/weight-selector/branch APIs reused. No migration. | customer-home-v2 OFF renders the existing homepage. Larger touch controls and suppressed promotional popups are scoped to the new experience. Reusable weight-dialog keyboard focus containment remains as an accessibility correction. |
| 8. Campaigns | `campaign/{HomepageCampaign,HomepageCampaignRepository,CampaignRequest,CampaignService,CampaignController}.java`, `storage/R2StorageService.java`, `exception/GlobalExceptionHandler.java`; `app/admin/homepage-campaigns/page.tsx`, `types/campaign.ts`, `lib/campaigns.ts`, `components/menu/CampaignMedia.tsx` | `V51__create_homepage_campaigns.sql` only; additive public/admin campaign/media APIs. | homepage-campaigns OFF serves an empty public list and restores default homepage content. Keep V51/table and R2 assets; no destructive downgrade needed. |

Shared admin wiring: `components/admin/{AdminSidebar,AdminMobileNav,BranchOperationalSettings}.tsx`.
The existing SettingField/SettingToggle help pattern is reused; new fields have labels,
operation descriptions and accessible label associations. `backend/build.gradle` adds the
independent `enhancementTest` source set and includes it in `check`; existing tests are not
removed or excluded.

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
and have a feasible minimum-quantity pickup within the horizon. At most four of each are
shown. No history means no fabricated trend; an optional failure omits the sections while
the real menu/quick-add remains. Exact customer quantities are checked at pickup/checkout.
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

## Customer journey and delivery extension seam

The new homepage reuses real branch selection, menu products, cart storage, ProductGrid,
piece controls, the validated grams/kg selector and the mobile floating cart summary.
Touch targets on that surface are at least 44px. Choosing another branch does not erase
the old cart; customers are explicitly directed to review it before mixing branches.
Optional trend/campaign failures do not block menu browsing. The enhanced home and pickup
screen suppress the existing social-follow popup to avoid interrupting ordering.

The journey is **branch and products -> pickup time -> customer details -> review/payment**.
Date/time choices refresh on cart/branch/date/mode changes and every 30 seconds. Abort
controllers and request keys discard stale responses; the Continue check also rejects
a response if the cart/selection changed while it was in flight. Network errors offer
Retry or the original selector, clearly stating that availability has not been confirmed.
No availability path clears cart or customer storage. A selection is only a preview until
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

Final worktree verification (2026-09-23, isolated synthetic data):

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

`./gradlew enhancementTest bootJar --quiet` passed separately from the legacy test
task. The final focused suite has 19 tests across seven classes; frontend build generated
45 pages. The real HTTP regression ran against the current packaged application and a
freshly migrated PostgreSQL database. Both browser scripts passed after that run.
No live R2 or payment integration success is implied by mocked/service tests.

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

All Flyway migrations through V51 and Hibernate schema validation were exercised on
isolated PostgreSQL 17. No real payment, courier or live R2 upload was performed. Real
DEV-bucket credentials, media quality/size review, actual branch policies and controlled
business acceptance remain rollout prerequisites. Feature OFF needs no DB rollback,
but it cannot reverse intentional admin tax changes, created slots/allocations, existing
orders/reservations, uploaded assets, or improvements to admin authorization.
