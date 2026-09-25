# SCRUM-13: DEV baseline and release evidence

**Observed on 25 September 2026 (IST).** Repository base: `dev` at
`67469a6795f67365af16f1295bb8f12393b189da`. This is an audit of source and
read-only public HTTP responses. It does not assert that the DEV deployment runs
this exact commit. Recheck the HEAD and deployment SHA before implementing any
subsequent story.

## Evidence legend

- **Code present**: an implementation path exists in this commit. Its real-world
  behaviour is not established by source inspection.
- **DEV observed**: a read-only request to `dev.gokulsweets.in` returned the
  stated result on the observation date; this does not establish backend health.
- **Unverified**: credentials, deployed backend URL, seeded business data,
  private telemetry or operational access were unavailable for this audit.

## Existing journeys and planned enhancements

| Area | Code evidence in `dev` | Baseline conclusion / next check |
| --- | --- | --- |
| Branch discovery and menu | `frontend/app/menu/page.tsx`, `components/branch/BranchSelector.tsx`, `backend/.../branch/BranchController.java`, `menu/MenuController.java` | **Code present.** Live branch catalogue, branch configuration, empty catalogue and unconfigured branch remain unverified. |
| Cart and pickup | `frontend/app/cart/page.tsx`, `app/checkout/pickup/page.tsx`, `backend/.../pickup/PickupSlotController.java` | **Code present.** Verify opening hours, active slots, partial availability, expiry and branch switching with known branch fixtures. |
| Inventory and production | `backend/.../inventory/service/InventoryAvailabilityService.java`, `inventory/automation/`, `inventory/production/`, `order/controller/CartAvailabilityController.java` | **Code present.** There are policy, dated allocation and cart preview paths. Test policy/stock/slot setup and concurrent last-unit purchase in a disposable DB; do not infer live stock accuracy. |
| Checkout, order and payment | `frontend/app/checkout/{customer,review,payment}/`, `app/orders/`, `backend/.../order/controller/OrderController.java`, `payment/controller/PaymentController.java`, payment provider package | **Code present.** Idempotency and payment refresh exist. Real payment provider callbacks, hold reconciliation and refunds are unverified. Do not initiate a payment during an audit. |
| IST | `backend/.../config/ApplicationClock.java`, `config/EnhancementProperties.java`, `frontend/lib/{businessTime,constants}.ts`, `frontend/tests/businessTime.test.cjs` | **Code present.** Zone-less legacy timestamps and explicit-offset timestamps need a deployment/DB audit around midnight; historical rows may have mixed origins. See `docs/runtime-configuration.md`. |
| Storefront and campaigns | `frontend/components/menu/CustomerHomeExperience.tsx`, `backend/.../config/StorefrontFeaturesController.java`, `campaign/`, migrations V51/V52 | **Code present.** DEV media, campaign targeting, editorial draft/publish/rollback and the proposed pre-home intent gateway are not established. Existing campaign implementation should be extended rather than replaced. |
| Customer identity and support | `backend/.../customer/CustomerAdminController.java`, migration V36, `frontend/app/profile/page.tsx`, `app/about/page.tsx` | **Partial foundation.** Admin contact directory exists. No customer OTP endpoint, linked support-case workflow or complete customer account ownership flow was found in the inspected paths. See SCRUM-36, SCRUM-42 and SCRUM-49. |
| Reviews and offers | `backend/.../review/`, `frontend/services/reviewApi.ts`, `rebate/`, `frontend/app/admin/notifications/page.tsx` | **Code present.** The admin “Offers” page manages rebates; its own text says publishing an offer does not send a notification. Verified review and service recovery behaviour requires a separate audit. |
| Delivery, loyalty, occasions, banquet | Checkout request uses `pickupSlotId` and `pickupType`; `CartAvailabilityController` rejects silently treating delivery as pickup. No corresponding customer delivery/banquet/Moments journey was found in the reviewed route inventory. | **Planned, not verified as existing.** SCRUM-29–35, SCRUM-38–40 and SCRUM-43–45 define separate gated additions. |
| Admin operations and reporting | `frontend/app/admin/`, `backend/.../reporting/`, `inventory/`, `pickup/` | **Code present.** Data quality, branch permissions, staff exception handling and per-order contribution cannot be inferred from a dashboard route. |

The repository has Flyway migrations through **V52**, with 50 versioned migration
files. Inspect the target database's Flyway history before any deployment; file
presence does not mean a migration ran in DEV.

## Public DEV observations

| Request | Result | Interpretation |
| --- | --- | --- |
| `GET https://dev.gokulsweets.in/` | HTTP 200, 79,737 bytes, ~10.8 s from this environment | A customer page responded. JavaScript interaction, branch data and deployment SHA were not verified. |
| `GET https://dev.gokulsweets.in/menu` | HTTP 200, 50,677 bytes, ~11.2 s | A menu route responded; this does not establish a populated branch menu. |
| `GET https://dev.gokulsweets.in/api/storefront/features` | HTTP 404 from Vercel `/404` | This request targeted the **frontend origin**, not a verified backend API URL. It says nothing conclusive about the separate backend's feature endpoint. |

The first `HEAD /` request timed out after 12 s while subsequent GETs returned
200. Record repeat measurements from the actual customer region before treating
that single observation as a performance baseline. The deployed frontend build's
`NEXT_PUBLIC_API_URL` and `NEXT_PUBLIC_API_BASE_URL` values, backend origin,
release SHA and private API status were not available here.

## Configuration and release risks to resolve next

1. `backend/src/main/resources/application.properties` sets five additive
   storefront flags (`smart-availability`, `smart-pickup-selection`,
   `inventory-automation-v2`, `customer-home-v2`, `homepage-campaigns`) to
   `true`, while `docs/storefront-enhancements.md` says they ship OFF. This is
   an actual source/documentation mismatch for SCRUM-14; do not change active
   deployment settings as part of this audit. Existing inventory enforcement
   and scheduler are separately enabled.
2. Customer order and payment paths are allowed by the current
   `common/security/SecurityConfig.java`; order detail/history use order
   references. Assess customer ownership and enumeration when implementing
   OTP in SCRUM-36, rather than assuming the current public paths are safe for
   a new signed-in account model.
3. `frontend/lib/constants.ts` reads `NEXT_PUBLIC_API_URL`, while admin
   services also read `NEXT_PUBLIC_API_BASE_URL`. Verify both point to the
   intended environment; public Next build settings are embedded at build time.
4. Earlier committed payment/storage credentials remain in Git history according
   to `docs/runtime-configuration.md`; rotate with providers if not already done.
5. `docs/storefront-enhancements.md` records isolated regression evidence and
   the normal backend test suite's earlier Jackson mismatch. That is historical
   evidence, not a fresh passing run for this SHA.

## Metrics to record before a pilot

No read access to DEV database, branch logs, payment provider or analytics was
available. These **baseline values are unknown**, not zero: paid orders/day,
cart-to-paid conversion, stock-related cancellations, on-time pickup, payment
unknown/pending duration, refunds, waste, support response time and contribution
per order. Collect by branch and IST service date from authoritative records;
exclude secrets, OTPs and precise customer GPS from analytics exports. Record
observation period and sample size, including any festival or promotion.

## Read-only / disposable-fixture verification checklist

1. From an approved DEV build, record frontend and backend deployed SHAs, API
   origins and actual feature endpoint response. Confirm both API URL variables,
   CORS, R2 and payment callback point to DEV, not production.
2. Query a configured branch, an unconfigured branch and a disposable branch
   with an empty catalogue. Confirm empty/error copy preserves navigation and
   does not claim availability. Avoid modifying operational branch data merely
   to manufacture this state.
3. In a **fresh disposable database** using the guarded fixtures documented in
   `docs/storefront-enhancements.md`, test mixed piece/gram carts, competing
   final-unit orders, a full slot, a stale date, a changed price and feature
   endpoint outage with both feature configurations. Never run fixture seeds
   against DEV/production business data.
4. With a non-IST browser and UTC server, verify 23:59/00:01 IST service dates,
   service-year rollover, hold/payment expiry and historical zone-less versus
   offset timestamps. Classify old DB rows before any repair.
5. Use provider sandbox credentials in DEV to test callback retry and an
   ambiguous payment; compare payment, order, hold and inventory records. Do
   not assert a successful payment from redirect alone.
6. Record links to screenshots/logs, command results, staff sign-off and the
   chosen remedy for each failed case on SCRUM-13. Move the issue to Done only
   after the merged PR's DEV deployment and the acceptance matrix are verified.

## Handoff

This document is a repository snapshot, not a live dashboard. SCRUM-14 should
first reconcile configuration defaults and document the DEV/PROD values. Each
subsequent story must fetch current `dev` again, run its relevant positive and
negative checks and leave a Jira comment with merged SHA and deployment evidence.
