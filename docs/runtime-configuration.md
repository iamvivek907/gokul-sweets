# Runtime configuration and time contract

The backend's documented settings live in `backend/src/main/resources/application.properties`.
The frontend's public build settings live in `frontend/lib/constants.ts`. Keep credentials
in the deployment environment, never in source or `NEXT_PUBLIC_` variables. The customer
storefront's published feature values come from `/api/storefront/features`.

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
| `gokul.features.smart-availability` / `GOKUL_FEATURES_SMART_AVAILABILITY` | OFF | Backend/product: optional availability preview and stricter pickup window/preparation checks; requires reviewed branch policies. Does not disable existing inventory enforcement when OFF. |
| `gokul.features.smart-pickup-selection` / `GOKUL_FEATURES_SMART_PICKUP_SELECTION` | OFF | Customer selector; effective only when smart availability is ON. OFF restores the original selector. |
| `gokul.features.inventory-automation-v2` / `GOKUL_FEATURES_INVENTORY_AUTOMATION_V2` | OFF | Inventory owner: bounds generation to the rolling horizon; enable only after branch policy, approval, buffer and allocation review. Does not change existing scheduler flag. |
| `gokul.features.customer-home-v2` / `GOKUL_FEATURES_CUSTOMER_HOME_V2` | OFF | Storefront owner: enhanced home; OFF shows the legacy home. |
| `gokul.features.homepage-campaigns` / `GOKUL_FEATURES_HOMEPAGE_CAMPAIGNS` | OFF | Content owner: public scheduled campaigns; rendering on the enhanced home also needs customer-home-v2. |
| `gokul.features.future-ordering-days` / `GOKUL_FEATURES_FUTURE_ORDERING_DAYS` | 30 days | Product/operations: inclusive global ceiling, validated 1–60; shorter branch/product limits still win. This is not a production-stock promise. |
| `gokul.features.ist-time-fix-enabled` / `GOKUL_IST_TIME_FIX_ENABLED` | ON | Operations/time: existing legacy timestamp correction. Roll out with `NEXT_PUBLIC_IST_TIME_FIX_ENABLED`; keep historical rows untouched until audited. |
| `inventory.enforcement-enabled` | Existing value: ON | Inventory safety: checks sellable stock on commit. **Do not change as an enhancement rollback.** |
| `inventory.automation.scheduler-enabled` | Existing value: ON | Inventory operations: existing scheduled generation. **Do not change as an enhancement rollback.** |
| `gokul.web.allowed-origins` / `GOKUL_ALLOWED_ORIGINS` | Localhost only | Security/operations: exact browser origins allowed to call backend. No wildcard or URL path. DEV and PROD must explicitly set their own origin list; never combine them. |
| `payment.enabled-providers`, `payment.default-provider` / `PAYMENT_ENABLED_PROVIDERS`, `PAYMENT_DEFAULT_PROVIDER` | Existing provider values | Payments: enable/default only configured providers; verify redirect, callback and secrets within the same environment. |
| `cloudflare.r2.*` / `R2_*` | Bucket/public URL defaults; credentials empty | Storage: image bucket and version URLs. DEV and PROD require separate deployed credentials and bucket policies. |

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
| `NEXT_PUBLIC_IST_TIME_FIX_ENABLED` | Existing browser interpretation of zone-less legacy timestamps; default ON, use only in coordination with backend time switch. |

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
| Production | `https://gokulsweets.in` | Separate production backend, `GOKUL_ALLOWED_ORIGINS=https://gokulsweets.in`, payment credentials, callback/redirect, database and storage. Never reuse DEV payment credentials or customer notifications. Keep enhancement switches OFF until a separate production rollout. |

The PhonePe redirect base is extended by `PhonePePaymentProvider` with
`/payment/{orderNumber}`; test a full payment return before setting a final value.
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
