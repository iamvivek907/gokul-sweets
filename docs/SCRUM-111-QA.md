# SCRUM-111 review and QA

This change adds optional normal-pickup recommendations, inclusive convenience fees, recoverable payment links, date freshness, readable quantities, published About content and private recruitment. No merge or deployment is authorised. Both Vercel Git deployment configurations remain disabled.

## Configuration and limitations

- `GOKUL_FEATURES_PICKUP_ADD_ONS` and `GOKUL_FEATURES_BRAND_CAREERS` default true in application.properties. Existing effective theme/quote/identity flags remain authoritative.
- Fee defaults to ₹0. A branch manager configures ₹0/₹5/₹10 or a custom amount and reviews the inclusive tax classification before enabling. Quote acceptance must be enabled with the existing signing key for stale-price protection.
- `PAYMENT_CHECKOUT_ENCRYPTION_KEY` is a stable base64 encoded 32-byte AES key; it falls back to the existing staff MFA encryption key. Never change it while encrypted attempts remain outstanding. Keep it server-side; no NEXT_PUBLIC setting. Checkout URLs use authenticated encryption and are exposed only through existing verified order/payment access.
- Recommendations are a bounded projection of branch basket aggregates. No customer calls the admin report API. Missing analytics hides suggestions and leaves checkout available.
- Normal-pickup product costs are missing. Spend-versus-extra-rebate checks protect incremental revenue, but do **not** establish profit. No automatic addition, discount, or profitability claim is made.
- Fees do not affect priority, delivery or occasion pricing. Inclusive fee tax is snapshotted separately from product tax. Reporting allocates the net fee to revenue and its tax to tax totals.
- About text/profiles start as drafts. Publish verified details and authorised photos; no fabricated team data. Recruitment is limited to authorised staff and their accessible branches.

## QA checklist

1. Configure branch A fee ₹5 at 5% inclusive tax. For normal pickup verify an additional ₹5 payable, a separate fee row and ₹0.24 inclusive fee tax. Repeat at ₹0, ₹10 and custom. Check priority/delivery/occasion have no fee. Change fee or tax after obtaining a quote: continuation must require reviewing the fresh quote. Paid orders keep their original snapshot. Check customer order, account View Order, admin detail, payment amount and reports.
2. With branch basket analytics populated, check no more than three photo cards at actual tax-inclusive portion prices. Existing cart, unavailable and occasion-only items must be excluded. Try adding the last stock twice from two tabs. For an unpaid order with its own holds, its reserved stock must remain available for updating that order. Adding invalidates the accepted quote and returns to price review. Disable/report outage: checkout must remain usable.
3. Test slab threshold gaps and capped discounts. Verify eligible spend excludes the fee. Do not show tiers whose additional rebate equals or exceeds required additional spend. Add a suggested portion, inspect the recalculated authoritative eligibility and review before payment.
4. Open PhonePe, close it and return: Retry Payment reuses the same valid provider attempt. Reload without local payment state and verify recovery of the stored URL. Choose Cancel order & keep cart: verify provider first, then immediate inventory and capacity release. Simulate provider outage: state/holds remain unchanged. Simulate paid response: preserve confirmation. Send late success after cancellation: existing refund reconciliation must retain captured money. Confirm no terminal polling and matching-cart recovery preserves unrelated new carts.
5. Navigate away through a storefront link while payment is pending: choose stay/retry, keep order for later, or verify/cancel and keep cart. Browser close uses the native unsaved-navigation prompt.
6. Set browser timezone to UTC and America/Los_Angeles. Save yesterday's date or an elapsed slot, then reload/focus/return to tab/cross India midnight. Show current available slots and require confirmation; keep cart and valid future preferences. Inject malformed, past and beyond-window date events: no intent saving or preview. Confirm date bounds update after focus and midnight and availability refreshes before continuing.
7. Verify 2000 g → 2 kg, 1250 g → 1.25 kg, 999 g → 999 g and unit products as pieces throughout review, account View Order, customer order and admin details. Backend grams/pricing are unchanged.
8. Admin About: save draft story and profiles in founder/team/developer sections. Upload/preview/replace/remove photos; change order and publication. Public pages show only published content and preserve branch directions, customer-care and policy links. Edit the same record in two tabs: the stale save/photo request must conflict without overwrite.
9. Create an active branch opening with requirements and experience. Apply on mobile with consent; verify confirmation and one application on retry/double-submit. Close the opening before submit: reject. Submit general interest. Check duplicate contact/role and abuse limits. Verify applicant PII never appears publicly.
10. Recruitment: search and filter branch, opening, experience and status; page past 25 applicants without losing filters. Review qualifications/contact/private notes and move through New, Reviewing, Shortlisted, Interview, Hired/Rejected. Check counts, empty/loading/error states, stale-update conflict and branch/permission denial. Verify list/detail access and updates in audit records.
11. Review About, Careers, recruitment, fees, add-ons and payment on desktop and 390 px phone widths; check contained scroll, readable prices, keyboard focus and touch controls. Retest notification and existing ordering regressions.

## Validation commands

Frontend: `npm run lint`, `npx tsc --noEmit`, `TZ=UTC node --test tests/*.test.cjs tests/*.test.mjs`, repeat with `TZ=America/Los_Angeles`, and production build with matching HTTPS API origins. A symlinked worktree can use `npm run build -- --webpack` for local validation; CI uses its normal isolated install.

Browser: run `tests/brand-careers.browser.mjs` with Playwright against port 3311, followed by existing inbox/alert/occasion regressions. API responses in that browser suite are synthetic; live backend/provider/storage QA remains necessary.

Backend: Java 21 `./gradlew --no-daemon clean build bootJar` against isolated PostgreSQL 17. The existing workflow applies/validates Flyway and Hibernate and runs both test suites. Added coverage includes fee math, quote invalidation, own reservations, provider outage/cancellation/late success, encrypted link integrity, drafts/publication, job closure, duplicate applicants, permissions and pagination.

## Current local evidence

- Frontend lint: passed (one pre-existing AdminMobileNav ref warning).
- TypeScript: passed.
- Node regressions: 46 passed in UTC and 46 passed in America/Los_Angeles.
- Production build: passed with webpack and a synthetic HTTPS API origin.
- Backend Java 21 build attempted: blocked resolving the existing Spring Boot 4.1.1 Gradle plugin in this environment. PostgreSQL binaries are available, but this environment permits only the root UID, which PostgreSQL refuses for server startup. Backend/Flyway execution is therefore pending CI.
- Browser execution attempted: Playwright browser downloads returned an unavailable-site response; the cloud browser also blocked the local frontend URL. Desktop/mobile browser suite has been added to CI, but no local browser pass is claimed.

Do not move SCRUM-111 to QA until CI, browser regression and required implementation review are complete. Keep the PR in draft while these checks are pending or blocked.
