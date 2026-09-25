# Runtime configuration and time contract

The backend's documented settings live in `backend/src/main/resources/application.properties`.
The frontend's public build settings live in `frontend/lib/constants.ts`. Keep credentials
in the deployment environment, never in source or `NEXT_PUBLIC_` variables. The customer
storefront's published feature values come from `/api/storefront/features`.

## Domains and environments

| Environment | Customer origin | Required deployment values |
|---|---|---|
| Development | `https://dev.gokulsweets.in` | `NEXT_PUBLIC_API_URL` points to the development backend; `GOKUL_ALLOWED_ORIGINS=https://dev.gokulsweets.in`; `PHONEPE_REDIRECT_URL=https://dev.gokulsweets.in/checkout` (the provider appends `/payment/{orderNumber}`); `PHONEPE_WEBHOOK_URL` points to the development backend webhook. Confirm a full payment return before enabling payments. |
| Production | `https://gokulsweets.in` | Separate production backend, origin, payment credentials, callback/redirect, database and storage configuration. Never reuse development payment credentials or customer notifications. |

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
