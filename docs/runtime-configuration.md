# Runtime configuration and time contract

The backend's documented settings live in `backend/src/main/resources/application.properties`.
The frontend's public build settings live in `frontend/lib/constants.ts`. Keep credentials
in the deployment environment, never in source or `NEXT_PUBLIC_` variables. The customer
storefront's published feature values come from `/api/storefront/features`.

For the Sprint 0–3 DEV manual QA candidate, see `docs/design/sprint-2-3-manual-qa-config.md`.
All boolean feature defaults in `application.properties` are ON for that candidate;
backend environment overrides can still disable them individually. Never deploy
these defaults to PROD without its own rollout approval.

## Configuration ownership and source of truth

`backend/src/main/resources/application.properties` is the single backend registry:
each setting has a nearby comment describing its effect and a deployment variable
where an override is needed. Spring `@ConfigurationProperties` classes validate
the values. `frontend/lib/constants.ts` is the single source for **both** customer
and admin API origins; frontend API services import from there. Changing a public
frontend value requires a new build. The backend feature endpoint publishes only
non-secret, customer-safe effective flags and the India business date.

| Backend property / deployment variable | Default | Effect, owner and dependency |
| --- | --- | --- |
| `gokul.features.smart-availability` / `GOKUL_FEATURES_SMART_AVAILABILITY` | ON for DEV QA | Backend/product: optional availability preview and stricter pickup window/preparation checks; requires reviewed branch policies. Does not disable existing inventory enforcement when OFF. |
| `gokul.features.smart-pickup-selection` / `GOKUL_FEATURES_SMART_PICKUP_SELECTION` | ON for DEV QA | Customer selector; effective only when smart availability is ON. OFF restores the original selector. |
| `gokul.features.inventory-automation-v2` / `GOKUL_FEATURES_INVENTORY_AUTOMATION_V2` | ON for DEV QA | Inventory owner: bounds generation to the rolling horizon; enable only after branch policy, approval, buffer and allocation review. Does not change existing scheduler flag. |
| `gokul.features.customer-home-v2` / `GOKUL_FEATURES_CUSTOMER_HOME_V2` | ON for DEV QA | Storefront owner: enhanced home; OFF shows the legacy home. |
| `gokul.features.homepage-campaigns` / `GOKUL_FEATURES_HOMEPAGE_CAMPAIGNS` | ON for DEV QA | Content owner: public scheduled campaigns; rendering on the enhanced home also needs customer-home-v2. |
| `gokul.features.future-ordering-days` / `GOKUL_FEATURES_FUTURE_ORDERING_DAYS` | 30 days | Product/operations: inclusive global ceiling, validated 1–60; shorter branch/product limits still win. This is not a production-stock promise. |
| `gokul.features.planned-pickup-production` / `GOKUL_FEATURES_PLANNED_PICKUP_PRODUCTION` | ON for DEV QA | SCRUM-33: label an available future pickup date as approved for preparation only when every cart line has an approved dated daily production allocation. Requires existing inventory enforcement and smart availability; OFF omits the label and retains the current capacity checks. Forecast alone never qualifies. The locked inventory reservation remains authoritative at order creation. |
| `gokul.features.planned-delivery-production` / `GOKUL_FEATURES_PLANNED_DELIVERY_PRODUCTION` | ON for DEV QA | SCRUM-34: future delivery quotes are unavailable until enabled. ON still requires all existing delivery prerequisites, approved dated daily production for every item, stock availability, production lead time and expected readiness before each offered rider window. Same-day delivery continues through its existing checks. An accepted quote, rider hold and locked inventory reservation remain authoritative at order creation. |
| `gokul.features.ist-time-fix-enabled` / `GOKUL_IST_TIME_FIX_ENABLED` | ON | Operations/time: existing legacy timestamp correction. Roll out with `NEXT_PUBLIC_IST_TIME_FIX_ENABLED`; keep historical rows untouched until audited. |
| `inventory.enforcement-enabled` | Existing value: ON | Inventory safety: checks sellable stock on commit. **Do not change as an enhancement rollback.** |
| `inventory.automation.scheduler-enabled` | Existing value: ON | Inventory operations: existing scheduled generation. **Do not change as an enhancement rollback.** |
| `gokul.web.allowed-origins` / `GOKUL_ALLOWED_ORIGINS` | Localhost only | Security/operations: exact browser origins allowed to call backend. No wildcard or URL path. DEV and PROD must explicitly set their own origin list; never combine them. |
| `gokul.web.environment-cors-enabled` / `GOKUL_ENVIRONMENT_CORS_ENABLED` | ON for DEV QA | CORS owner: when ON, `GOKUL_DEPLOYMENT_ENVIRONMENT=DEV` accepts only `https://dev.gokulsweets.in`; PROD accepts only `https://gokulsweets.in`. A conflicting explicit `GOKUL_ALLOWED_ORIGINS` rejects startup. OFF retains the existing allowlist. This can be enabled independently of the wider environment-isolation flag. |
| `payment.enabled-providers`, `payment.default-provider` / `PAYMENT_ENABLED_PROVIDERS`, `PAYMENT_DEFAULT_PROVIDER` | Existing provider values | Payments: enable/default only configured providers; verify redirect, callback and secrets within the same environment. |
| `cloudflare.r2.*` / `R2_*` | Bucket/public URL defaults; credentials empty | Storage: image bucket and version URLs. DEV and PROD require separate deployed credentials and bucket policies. |
| `gokul.environment-isolation.*` / `GOKUL_ENVIRONMENT_ISOLATION_ENABLED`, `GOKUL_DEPLOYMENT_ENVIRONMENT`, `GOKUL_PUBLIC_API_ORIGIN` | ON / environment values required for DEV QA | Operations: when ON, backend fails startup unless the declared DEV or PROD storefront matches exact CORS, PhonePe return base and backend webhook; requires explicit PostgreSQL URL, separated R2 bucket and public URL, and PhonePe identifiers. Configure those dependencies before deploying PR #103. |

Other preparation, reservation, lifecycle, payment timeout, printing and report
settings stay in the same backend properties file with their existing defaults.
Secrets (`DATABASE_*`, `PHONEPE_*`, `RAZORPAY_*`, `PAYTM_*`, R2 keys and
`PRINT_AGENT_API_KEY`) must be provisioned in the environment and must never
be published by `/api/storefront/features` or a `NEXT_PUBLIC_*` variable.

### Frontend build settings and fail-closed checks

| Build variable | Purpose |
| --- | --- |
| `NEXT_PUBLIC_API_URL` | Required HTTPS **origin only** in a production build for customer API requests. Development without it uses `http://localhost:8080`. |
| `NEXT_PUBLIC_API_BASE_URL` | Optional compatibility variable for admin requests; if present, must exactly match `NEXT_PUBLIC_API_URL`. Both paths use one validated origin. |
| Branch FSSAI licence | Set each branch's 14-digit licence number in Admin → Branches. The customer footer reads the currently selected active branch from the backend; no frontend environment variable or rebuild is needed for edits. |
| `NEXT_PUBLIC_IST_TIME_FIX_ENABLED` | Existing browser interpretation of zone-less legacy timestamps; default ON, use only in coordination with backend time switch. |
| `NEXT_PUBLIC_MSG91_WIDGET_ID`, `NEXT_PUBLIC_MSG91_WIDGET_TOKEN` | Browser-scoped MSG91 Widget ID and restricted widget token. The optional verification entry point stays hidden if either is absent or the backend readiness endpoint is OFF. Never set these to the server Authkey. Rebuild after changing them. |

The production build rejects a missing API URL, non-HTTPS URL, malformed URL,
embedded credentials, path/query and mismatched customer/admin origins. This
prevents silent fallback to localhost and accidental use of two backends in
one customer session. It does **not** determine whether an otherwise valid
HTTPS hostname belongs to DEV or PROD: the deployment owner must verify the
actual built URL, backend CORS setting, webhook and provider credentials before
publishing. Preview deployments need explicit appropriate API settings too.

## Domains and environments

| Environment | Customer origin | Required deployment values |
|---|---|---|
| Development | `https://dev.gokulsweets.in` | Both `NEXT_PUBLIC_API_URL` and, if present, `NEXT_PUBLIC_API_BASE_URL` target the same HTTPS DEV backend; `GOKUL_ALLOWED_ORIGINS=https://dev.gokulsweets.in`; `PHONEPE_REDIRECT_URL=https://dev.gokulsweets.in/checkout` (the provider appends `/payment/{orderNumber}`); `PHONEPE_WEBHOOK_URL` points to the DEV backend webhook. Enable individual enhancement variables only after DEV checks. |
| Production | `https://gokulsweets.in` | Separate production backend, `GOKUL_ALLOWED_ORIGINS=https://gokulsweets.in`, payment credentials, callback/redirect, database and storage. Never reuse DEV payment credentials or customer notifications. Override enhancement switches OFF for production until a separate production rollout. |

The PhonePe redirect base is extended by `PhonePePaymentProvider` with
`/payment/{orderNumber}`; test a full payment return before setting a final value.
For example, the DEV base is `https://dev.gokulsweets.in/checkout`; the complete
return is `https://dev.gokulsweets.in/checkout/payment/{orderNumber}`. With
`GOKUL_ENVIRONMENT_ISOLATION_ENABLED=true`, set `GOKUL_DEPLOYMENT_ENVIRONMENT=DEV`
or `PROD` and `GOKUL_PUBLIC_API_ORIGIN` to the matching HTTPS backend origin.
The startup guard checks the actual webhook URL ends in
`/api/payments/webhooks/phonepe`, rejects combined DEV/PROD CORS, rejects the
shared default R2 bucket, and disallows PhonePe sandbox on PROD. Set separate
`R2_BUCKET_NAME` values before turning it on. This guard cannot establish that
two different database URLs, payment clients, R2 keys or message providers
really belong to different accounts; verify those identities outside this app.
The frontend `NEXT_PUBLIC_*` values are embedded at build time. Build each environment
with the corresponding API URL and toggle, or adopt an explicit runtime config endpoint.
Do not infer the backend hostname from the storefront domain.

## Time

- `ApplicationClock.BUSINESS_ZONE` owns the current India business zone:
  `Asia/Kolkata`. Inventory's `Clock` now uses the same zone.
- Pickup `LocalDate` and `LocalTime` are business calendar values. Legacy
  `LocalDateTime` database values represent IST wall time, without an offset.
- `GOKUL_IST_TIME_FIX_ENABLED` defaults to true. When true, order and pickup
  entity callbacks write IST timestamps instead of the host's default timezone.
- `NEXT_PUBLIC_IST_TIME_FIX_ENABLED` defaults to true. It interprets zone-less
  timestamp strings returned by the backend as IST. Strings with `Z` or an
  explicit offset retain their stated instant. The frontend formats customer
  order and payment timestamps in IST.
- These rollout switches should move together. A rollback does **not** rewrite
  existing rows. Audit historical order/pickup rows created while a host ran in
  UTC before deciding on a one-time data migration. Do not add five and a half
  hours to every row blindly: payment rows may already have been written in IST.
- A future schema change should store expiry and event instants with offsets or
  UTC instants, while retaining a separate branch-local service date and slot.

## Credential recovery

Earlier commits contained storage and payment credential defaults. This change
removes the defaults from the current tree, but the old values remain in Git
history. Rotate the affected R2 and payment/webhook credentials with their
providers, then update the matching deployment environments. Rewriting Git
history alone does not invalidate a credential.

## DEV / PROD isolation handoff (SCRUM-16)

Before enabling the switch, record a **redacted** inventory for each deployment:
frontend build SHA and `NEXT_PUBLIC_API_URL`, backend SHA and public API origin,
`GOKUL_ALLOWED_ORIGINS`, `PHONEPE_REDIRECT_URL`, `PHONEPE_WEBHOOK_URL`, payment
provider mode and credential **identifiers**, database host/name, R2 account and
bucket, OTP sender/project, push app, analytics property, SMS/WhatsApp/email
sender and notification enable flags. Never record tokens, passwords or key
material in Jira. DEV and PROD must have different writeable DBs, R2 buckets,
payment webhook secrets and customer messaging credentials. Disable customer
messages on DEV until a test recipient allowlist exists. OTP is implemented behind provider and verification gates; push and other outbound
messaging require their own isolation if added. Analytics must use separate properties or remain
OFF in DEV.

With the isolation switch ON, new PhonePe merchant order IDs use
`GKS-DEV-PPE-{id}` or `GKS-PROD-PPE-{id}`. Existing persisted
`GKS-PPE-{id}` payments still verify using their stored provider order ID.
This prevents a new callback for the same numeric payment ID from matching
the other environment's new payment row; separate webhook credentials and
provider accounts are still required.

### DEV CORS and page-load check (SCRUM-16)

For the Render backend at `https://api-dev.gokulsweets.in`, set
`GOKUL_DEPLOYMENT_ENVIRONMENT=DEV` and `GOKUL_ENVIRONMENT_CORS_ENABLED=true`.
Set `GOKUL_ALLOWED_ORIGINS=https://dev.gokulsweets.in` or remove that override;
the enabled policy derives this exact origin. A conflicting explicit value
causes startup to fail instead of silently adding PROD. Do **not** add
`https://api-dev.gokulsweets.in` as an allowed browser origin: that is the
destination API host. Keep `GOKUL_ENVIRONMENT_ISOLATION_ENABLED=false` until
its separate database, R2 and payment prerequisites are configured.

In Vercel, set `NEXT_PUBLIC_API_URL=https://api-dev.gokulsweets.in` and
either unset `NEXT_PUBLIC_API_BASE_URL` or set it to the same origin. Rebuild
the DEV frontend after changing these build-time variables. Confirm the Vercel
domain is `https://dev.gokulsweets.in`, with no trailing slash or path in any
of the origin variables.

After Render is awake, check this read-only preflight:

```bash
curl -i -X OPTIONS 'https://api-dev.gokulsweets.in/api/storefront/features' \
  -H 'Origin: https://dev.gokulsweets.in' \
  -H 'Access-Control-Request-Method: GET'
```

Expect `Access-Control-Allow-Origin: https://dev.gokulsweets.in`, then open
the DEV home and menu and confirm `/api/storefront/features` returns JSON.
Repeat the preflight with `Origin: https://gokulsweets.in` and confirm no
allow-origin header. A Render sleeping-service page or timeout is not evidence
that CORS is correct or incorrect; wait for health/JSON before diagnosing CORS.

Rotate credentials known to have existed in Git history (R2 access keys and
payment/webhook secrets): revoke the old key at its provider, provision new
scoped credentials per environment, update the environment store, then confirm
the old key no longer works. This requires access to the providers and secret
stores; a code PR alone cannot perform or verify rotation.

DEV release gate: inspect the embedded frontend API origin; start backend with
the isolation switch ON; verify an OPTIONS request from the DEV origin succeeds
and one from PROD is denied; create a sandbox payment and verify its return is
the DEV checkout URL; send a signed sandbox webhook only to the DEV backend,
repeat the same event to confirm idempotency, and verify it changes no PROD
order. Check R2 upload/delete and DB writes hit DEV resources only. Send no
real customer notifications. Exercise one intentionally mismatched redirect
and one combined CORS configuration in a disposable deployment and confirm
startup is rejected. Check the flag OFF returns to existing configuration
behaviour. Record exact SHA, provider mode, redacted settings, evidence, and
pending verification in Jira before declaring this story done.

## Release checks

1. Confirm the frontend build's API URL, origin allowlist, provider redirect and
   webhook all point to the *same* environment.
2. With a browser in UTC or another timezone, choose an IST slot just before
   and just after midnight. Check pickup date, countdown, payment expiry,
   confirmation and order history.
3. Confirm a pending reservation expires at the backend's time and does not
   appear expired early in the browser. Test a paid order before the expiry.
4. Verify order timestamps already saved in production before attempting any
   historical repair. Reconcile mixed origin rows individually.
5. After rollout, remove the temporary toggles when the migration and customer
   timestamp contract are stable.

## SCRUM-105 branch experience

`GOKUL_FEATURES_BRANCH_EXPERIENCE=false` by default. Owner: branch operations. Dependencies: V79, R2 public storage, approved branch artwork, `BRANCH_MANAGE` scoped staff. OFF keeps existing branch cards and About link. ON displays only published branch covers/copy and a branch-specific details route; pickup eligibility remains backed by branch pickup settings. Drafts never reach public responses. See `docs/design/SCRUM-105-branch-experience.md` for QA and rollback.

`GOKUL_FEATURES_OCCASION_BULK_PRODUCTION` (ON by default for owner-requested DEV QA; SCRUM-35, owner: branch manager): requires occasion enquiries and payments for checkout. ON creates a dedicated per-item production plan automatically when a pickup quote is approved with kitchen-ready time in IST. Daily online inventory is unchanged. Manager must review procurement and existing commitments; automated aggregate kitchen scheduling is not yet provided. OFF blocks starting checkout for dedicated plans while existing attempts still reconcile. Legacy attempts continue their original daily-inventory lifecycle. No production activation until DEV provider/manager QA.


## Sprint 4 QA handoff — owner-requested defaults

SCRUM-33, SCRUM-34 and SCRUM-35 are ready for owner testing after the QA configuration PR is merged and its exact merged SHA is deployed to DEV. Planned pickup, planned delivery, occasion enquiries, occasion payments and occasion bulk production now default ON in application.properties. Any explicit server environment value wins; remove a stale false override or set the matching GOKUL_FEATURES_* variable true in DEV. These defaults are not a production sign-off; production can retain explicit false overrides. No secrets are changed.

Prerequisites: apply migrations through V89, verify DEV identity/MSG91, environment isolation, CORS, configured PhonePe test account and redirect, product taxes, branch permissions and real pickup slots. Planned delivery additionally needs approved dated allocations, kitchen-ready times, configured coverage/pin, economics, rider windows and availability. Bulk orders use dedicated manager-approved production instead of daily retail stock. No separate frontend rebuild-time feature flag is needed; effective flags come from the backend.

Owner QA scenarios:
- SCRUM-33 / SCRUM-71: future pickup approved production versus draft forecast, lead time, held/committed quantities, exhausted slots, staff audit and IST boundary.
- SCRUM-34 / SCRUM-72: future delivery kitchen readiness plus rider capacity, address/date requote preserving cart, last-capacity concurrency and cancellation/payment reconciliation.
- SCRUM-35 / SCRUM-73: request 100 kg; approve price, deposit, balance deadline and readiness; verify automatic dedicated allocation without retail-stock changes; pay deposit/balance; record partial/full physical readiness; test stale staff edits, expiry and pre-preparation cancellation. Verify late payments enter refund review and never confirm cancelled orders. PhonePe refund execution remains manual; cancellation after preparation and automated aggregate kitchen capacity remain outside this implementation.

Repeat with each relevant flag OFF using DEV environment overrides and restore ON afterwards. Record deployed frontend/backend SHAs, branch, IST time, flag values, actual results and defects in QA subtasks. In QA is not Done; no deployment or provider/device/staff test result is claimed by this configuration change.

## Sprint 5 notification inbox (SCRUM-39)

Baseline: merged `dev` `c99e8a38d7cd36297c0ef5ca573e02bd0cd2c71b` (PR 138).

| Setting | Default | Owner / dependency | OFF fallback |
| --- | --- | --- | --- |
| `GOKUL_FEATURES_NOTIFICATION_INBOX` | `false` | Customer service; verified OTP identity, trusted HTTPS Origin, DEV/PROD isolation and abuse-controls verification; account hub for the profile entry point | Existing order history, tracking and occasion request history |

Migration **V90** adds durable customer notification events and channel preferences.
There are no new admin columns. Existing payment, staff readiness and delay actions
create messages automatically for exact verified owners, within the source transaction.
A rollback removes both the source change and message; unique source/status keys suppress
concurrent insert/replay duplicates. Flag OFF stops new recording and hides the API/UI;
turning it back ON preserves prior read state. There is **no historical backfill**.

Supported events: verified standard-order payments, provider-confirmed refunds, late-payment
refund reviews, pickup/delivery kitchen-ready transitions, revised pickup estimates,
and occasion deposit/balance receipts and late-payment refund reviews. Delivery kitchen-ready
never claims delivered. Refund-review never claims refunded. The provider's existing refund
capabilities are unchanged. Occasions link their exact request; the resulting order's readiness
links its exact order. A message is an immutable historical event; its link shows current status.
All timestamps are explicit instants displayed in Asia/Kolkata.

Customers find **Profile → Notification inbox**, with unread/read state, refresh and bounded
older-message pagination. Lost-network refresh retains already loaded messages; read status
changes only after server acknowledgement. No notification data is saved to browser storage.
The offer channel defaults OFF and cannot opt in without current versioned marketing consent;
withdrawal remains in Privacy and data choices. The preference does not itself grant consent.
**Offer broadcasting, browser push, sound, SMS and email dispatch are not part of this PR.**
They require subsequent sender/device work; there is no external-send claim or delivery state.
`AVAILABLE` means a message is persisted in the in-app inbox, not read or externally delivered.

### DEV checks after merge and deployment

1. Apply V90 and enable the inbox switch in DEV only after OTP/Origin prerequisites work.
   Keep Sprint 4 QA switches as configured. Sign in; open Profile → Notification inbox.
2. Pay a new owner-bound order and replay the provider callback: one payment message.
   Mark ready through authorized staff actions: one ready message; another customer's inbox
   and a revoked session cannot access it. Guest orders are not imported by phone.
3. Publish a revised pickup estimate, retry it, then revise it again: one event per distinct
   update. Read one message from two tabs; the timestamp stays unchanged on retry.
4. Verify occasion deposit/balance messages open the exact request after asynchronous history
   loads. Late success shows refund review, never a confirmed refund. Provider refund completion
   is separately validated on a provider that supports it.
5. Lose network during refresh/read, recover, paginate older messages and check mobile,
   keyboard and desktop. Use a non-IST browser zone across India midnight.
6. Toggle OFF: new events stop; order/occasion history still works. Toggle ON: existing read
   state returns, with no old-event backfill. Optional offer preference starts OFF; marketing
   opt-out is separate from essential transaction messages.

Tests: `CustomerNotificationInboxIntegrationTest`, identity controller and effective-flag tests,
existing payment/workflow regressions, and `frontend/tests/customer-notification-inbox.browser.mjs`.
Run the browser regression with a local Next server and `PLAYWRIGHT_MODULE` pointing to an
installed Playwright module; `BROWSER_BASE` defaults to `http://127.0.0.1:3311`.
Deployment/provider/staff DEV verification remains an owner QA step; CI alone does not prove it.
SCRUM-39 remains open until its remaining sender work and DEV evidence are complete;
SCRUM-40 depends on this inbox foundation.

## Sprint 5 — optional customer browser alerts (SCRUM-40)

Baseline: merged PR 139, `dev` `92836c6fab1b78f3940ea329036b8eec8c82a856`.
Migration **V91** adds session-bound push subscriptions, bounded delivery attempts, sound
preferences and quiet hours. No new admin columns are introduced. Customer controls include
help text for permission denial, sound activation, device mute, IST times and inbox fallback.

| Setting | Default | Purpose |
| --- | --- | --- |
| `GOKUL_FEATURES_NOTIFICATION_ALERTS` | `false` | Enables customer alert controls and sender; also requires the inbox and verified-identity prerequisites |
| `GOKUL_WEB_PUSH_PUBLIC_KEY` | empty | Base64url uncompressed P-256 public VAPID key; exposed to the signed-in browser |
| `GOKUL_WEB_PUSH_PRIVATE_KEY` | empty | Base64url 32-byte P-256 private VAPID key; backend secret only |
| `GOKUL_WEB_PUSH_SUBJECT` | empty | Operator-controlled `mailto:` contact or HTTPS contact URL |

Keep both notification switches OFF until DEV configuration is approved. Existing Sprint 4
QA flags are unchanged. Generate a distinct key pair per environment on an operator machine:

```js
const { createECDH } = require('node:crypto');
const key = createECDH('prime256v1');
key.generateKeys();
console.log('Public:', key.getPublicKey().toString('base64url'));
console.log('Private:', key.getPrivateKey().toString('base64url'));
```

Store the private key in backend deployment secrets, never frontend environment variables,
source control or Jira. Supply a real operator contact as subject. Missing/invalid configuration
keeps push unavailable; sound and the inbox remain usable. HTTPS is required outside localhost.
Key rotation requires re-registering browser subscriptions.

Permission is requested only after **Enable browser notifications** is tapped. Registration
binds to the exact verified account, environment and current browser session (existing 30-day absolute customer-session lifetime). Maximum five
live browsers per account; logout, session revocation/expiry, explicit disable and provider
404/410 stop future sends. Permission revocation can be removed by the browser/provider;
the customer can explicitly disable the stored registration. Failed disables do not claim success.
Browser support follows standard Web Push; iOS/iPadOS 16.4+ requires a Home Screen web app.
See [WebKit guidance](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/).

The worker selects committed, unread transaction messages created after subscription and
within five minutes. Before sending an order-stage push it rechecks the current stage; superseded stage alerts
are skipped while historical inbox messages remain. It leases work in the database, sends
up to three tasks per batch, retries
429/5xx/network failures after 60 seconds, and stops after three attempts. It uses AES128GCM
and VAPID from [webpush-java](https://github.com/web-push-libs/webpush-java). Only allowlisted
HTTPS browser-provider endpoints are accepted; redirects are disabled and requests time out.
`ACCEPTED` means provider acceptance, not device delivery or customer read. External delivery
is best effort, not exactly once. The service worker serializes push events and retains the last
256 event IDs to suppress repeat alerts, with stage-specific copy, a brand icon/badge and a validated exact order/request
link. Copy includes order reference, branch, booked pickup time or revised ready time when
applicable; never customer phone, address, items or payment amounts. It never plays custom audio. Marketing broadcasting is excluded.

Sound defaults OFF. **Activate and test sound in this tab** explicitly unlocks browser audio;
the separate saved sound preference permits one short chime for fresh unread updates while
the page is visible. Initial/history loads stay silent. Web Locks and a metadata-only cursor
coordinate tabs; unsupported coordination/storage falls back to silence. Device mute and
browser autoplay rules still apply. No background custom sound is promised.

Quiet hours default to **22:00–08:00 IST**, inclusive start/exclusive end, across midnight.
They suppress push and in-page sound while messages remain in the inbox. Skipped alerts do
not burst after quiet hours. Alert flag OFF hides controls and stops the sender/runtime;
existing order history and inbox behavior follow their own switches. Stored read state remains.

After merge and DEV deployment, enable the two switches with configured VAPID credentials.
Test a fresh payment/ready/delay/occasion message on Chromium, Firefox and a supported iOS
Home Screen app. Check stage-specific locked-screen copy and the exact order/request on click; deny/revoke permission,
logout/expire the session, simulate offline enable/disable, provider rejection and retry, multiple
tabs, browser/device mute, quiet boundaries and flag OFF. Confirm the inbox remains authoritative.
Automated tests mock external delivery and permission/audio APIs; they do not prove real
provider acceptance, OS delivery or device audio. Record that DEV evidence before moving
SCRUM-39/40 to QA or Done.


Customer stages now include payment-confirmed booking (with booked pickup time in IST),
preparation, kitchen ready, pickup/delivery completion, dispatch, cancellation, pickup-window
expiry and no-show, plus revised ready estimates and truthful refund states. The persisted
transition owns its message; duplicates do not invent new stages. Future bookings receive
ready push even while the PWA is closed, subject to permission, live session, quiet hours and
provider/device support. The header bell shows unread count; inbox cards use stage icons,
unread badges and clear links. Device notification layout remains OS controlled.

Staff new-order/preparation reminders and overdue email escalation are implemented in the
SCRUM-109 follow-on below. Customer push alone does not cover staff operational alerts.

## Sprint 5 — staff operational notifications (SCRUM-109)

Baseline: merged PR 140, `dev` `c3eeba9c57b6ca10c80c29ed482866986578e802`.
Migration **V92** adds branch order alerts, per-staff read state, live-session push registrations
and leased push/email delivery attempts. Staff UI: the topbar bell and
**/admin/staff-notifications**. Existing **/admin/notifications** remains offer management.
Alert links open **/admin/orders/{orderNumber}**, with existing preparation/ready/delay APIs,
permissions, eligibility and inventory checks. All new controls include explanatory help text.
No staff email column is added, and no address is inferred from usernames.

| Setting | Default | Owner / dependency |
| --- | --- | --- |
| `GOKUL_FEATURES_STAFF_ORDER_ALERTS` | `false` | Operations; secure staff sessions, DEV/PROD isolation and trusted Origin/CSRF |
| `GOKUL_STAFF_ALERTS_REMINDER_MINUTES` | `10` | Operations; 1–60 minutes before the existing preparation window opens |
| `GOKUL_STAFF_ALERTS_ESCALATION_MINUTES` | `5` | Operations; 1–60 minutes after booked pickup/service time for unresolved email escalation |
| `GOKUL_STAFF_ALERTS_EMAIL_ENABLED` | `false` | Operations; enables the email adapter only with valid configuration |
| `GOKUL_STAFF_ALERTS_EMAIL_API_KEY` | empty | Backend secret: Resend sending API key, scoped to a verified sender domain |
| `GOKUL_STAFF_ALERTS_EMAIL_FROM` | empty | Operator-approved verified sender address, e.g. a mailbox on the verified domain |
| `GOKUL_STAFF_ALERTS_EMAIL_RECIPIENTS` | `{}` | Backend-only JSON mapping real staff IDs to operator-verified recipients; e.g. `{"12":"verified-staff@example.invalid"}` is syntax only |
| `GOKUL_WEB_PUSH_PUBLIC_KEY/PRIVATE_KEY/SUBJECT` | empty | Existing shared VAPID configuration; private key stays backend-only |

The staff flag is independent of customer inbox/push toggles. Existing Sprint 4 QA flags stay
unchanged. Push needs explicit permission and registration on each staff browser, with at most
five live registrations per staff account. Registration is tied to the current secure staff
session, whose existing maximum lifetime is eight hours. Logout/expiry/staff edits/provider
404/410 stop sends to that registration. Re-register after signing in again. Denial, unsupported
browsers or missing configuration fall back to the queue/inbox. iOS Home Screen limitations and
OS sound controls are explained in the UI; no guaranteed custom background audio is claimed.
Customer and staff push IDs use different namespaces. Disabling staff push revokes only its
server registration so a shared customer subscription is not inadvertently removed.

Paid order confirmation records a unique NEW_ORDER alert in the payment transaction, including
fully paid occasion orders when finalized. Pending, failed or late-payment refund-review states
do not create a paid-order promise. Rollbacks remove the alert. Reminders reuse
PreparationEligibilityService for NORMAL/PRIORITY/admin override and delivery lead windows;
no new preparation rule or automatic order-status change is introduced. The worker checks
live CONFIRMED/PREPARING orders in keyset pages every 30 seconds while enabled:

- PREPARATION_SOON from the configured reminder boundary until eligibility opens.
- PREPARATION_DUE from eligibility until booked pickup/service start.
- PREPARATION_OVERDUE if still CONFIRMED at booked time; READY_OVERDUE if still PREPARING.

Timing uses Asia/Kolkata, including across India midnight. Reminder keys include their schedule,
so rescheduled windows are distinct; stale-window or already-completed actions are rechecked
before sending and skipped. The overdue scan covers the last day, not historical orders.
Branch-scoped inbox/sender authorization requires active staff, branch access (OWNER_ADMIN
may access all branches), ORDER_VIEW and the relevant preparation/ready permission. Permissions
are checked again before each attempt, so role/branch changes are respected. Staff who cannot
perform that action do not receive that reminder. NEW_ORDER needs ORDER_VIEW only.

Reading is per staff and idempotent. It suppresses that staff member's pending push, but does
not mark an order prepared/ready or suppress unresolved email escalation. Configured eligible
recipients get one email attempt sequence per overdue event after the escalation threshold.
Starting preparation ends preparation-overdue escalation; if the order is still not ready after
booked time, the separate ready-overdue action can escalate. Each task is leased with SKIP
LOCKED, max three attempts and 60-second retry backoff for 429/5xx/network errors. Push is limited
to fresh unread events after registration; queued stale alerts are skipped. Worker batches process
up to ten sends; provider calls have five-second connect and ten-second request bounds.

Email uses the [Resend HTTPS send API](https://resend.com/docs/api-reference/emails/send-email)
and stable environment/event/staff [idempotency keys](https://resend.com/docs/dashboard/emails/idempotency-keys).
Keep recipients and payload unchanged during retries; a provider idempotency conflict fails
rather than claiming another delivery. ACCEPTED records HTTP/provider acceptance, not mailbox
or OS delivery, read or order action. Generic operational copy includes order reference, branch
and IST timing, never customer phone/address/items/payment amounts. No provider response body,
email recipient or secret is logged. Email is optional and no external account is provisioned
or message sent by this implementation task.

**Deployment prerequisite:** scheduled reminders require an always-on backend/worker.
[Render free services](https://render.com/docs/free) sleep without inbound traffic; an asleep
service cannot execute these reminders. HTTPS avoids their SMTP-port restriction, but does not
remove the sleep limitation. Do not claim timely background operation on a sleeping deployment.
Flag OFF stops recording/generation/sending, hides the bell and retains the queue as fallback;
server push revocation is still allowed. Existing read state is preserved. There is no paid-order
historical backfill or new alerts for completed/cancelled orders.

After merge, apply V92 in DEV, keep production OFF, configure VAPID and Resend sender/recipient
secrets, and enable the staff flag on an always-on service. Use two staff accounts from different
branches plus a read-only role. Create/pay an order with the portal closed; confirm only eligible
staff get a useful alert and the exact authorized order opens after sign-in. Verify each timing
boundary, action before send, read-without-action email follow-up, provider denial/retry/410,
logout/expiry/permission change, future reschedule, two workers, offline enable/disable/read,
mobile/desktop, iOS Home Screen, no eligible staff and flag OFF. Verify staff email receipt with
the actual approved recipient. CI mocks external delivery; real DEV provider/device/staff evidence
is still required before moving SCRUM-109 to QA/Done. Preserve earlier owner QA stories.
