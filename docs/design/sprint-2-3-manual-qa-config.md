# Sprint 0–3 manual DEV QA configuration

`application.properties` now defaults all 33 boolean `gokul.features.*` switches to `true` (the numeric future-ordering-days remains 30). That includes the Sprint 0/1 pickup and payment safeguards, Sprint 2 storefront and staff login, and Sprint 3 identity, delivery, economics and dispatch. Each matching `GOKUL_FEATURES_*=false` backend environment override takes precedence. Check and remove any old OFF overrides from Render before testing. This is a DEV test candidate: do not deploy the same defaults to production until QA is complete.

The other rollout decisions are intentional: `inventory.enforcement-enabled=true`, `inventory.automation.scheduler-enabled=true`, `gokul.features.ist-time-fix-enabled=true`, strict DEV CORS and environment isolation ON; `order.pickup-lifecycle.automatic-expiry-enabled=false` until pickup expiry timing is approved; `GOKUL_IDENTITY_PROVIDER_ABUSE_CONTROLS_VERIFIED=false` until MSG91 limits are verified; `GOKUL_IDENTITY_PROTECT_LEGACY_ROUTES=false` until payment-return smoke; `GOKUL_IDENTITY_PHONE_ONLY_ORDER_RECOVERY=false` until the owner chooses that policy. Do not mark any of those three identity decisions true just to make the feature endpoint look enabled. Test them deliberately after prerequisites.

## Configure the DEV backend before deploying

Set these in the **backend server environment**, never in Git. Do not copy production provider credentials or customer data into DEV.

| Environment variable | Required value or review |
| --- | --- |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Separate DEV PostgreSQL database; Flyway V73–V75 must apply. |
| `GOKUL_DEPLOYMENT_ENVIRONMENT`, `GOKUL_PUBLIC_API_ORIGIN`, `GOKUL_ALLOWED_ORIGINS` | `DEV`, HTTPS backend origin without a path, and exactly `https://dev.gokulsweets.in`. Isolation and environment CORS default ON and reject inconsistent startup wiring. |
| `GOKUL_ENVIRONMENT_ISOLATION_ENABLED`, `GOKUL_ENVIRONMENT_CORS_ENABLED` | Already default `true` in this candidate. Remove old `false` backend overrides. Set false only for a targeted rollback/test. |
| `PHONEPE_REDIRECT_URL`, `PHONEPE_WEBHOOK_URL` | `https://dev.gokulsweets.in/checkout` and `<GOKUL_PUBLIC_API_ORIGIN>/api/payments/webhooks/phonepe`. |
| `PHONEPE_CLIENT_ID`, `PHONEPE_CLIENT_SECRET`, `PHONEPE_WEBHOOK_CHECKSUM_KEY_ID`, `PHONEPE_WEBHOOK_CHECKSUM_SECRET` | DEV or sandbox payment credentials and webhook credentials. Verify the configured base and authorization URLs match the provider environment. |
| `R2_BUCKET_NAME`, `R2_PUBLIC_URL`, `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | Dedicated DEV bucket (not the default shared bucket) and its HTTPS public URL and credentials. |
| `GOKUL_MSG91_SERVER_AUTHKEY`, `GOKUL_IDENTITY_RATE_LIMIT_KEY`, `GOKUL_IDENTITY_TRUSTED_PROXY_CIDRS` | Verified DEV SMS provider credential, random key of at least 32 characters, and only actual proxy CIDRs if forwarding headers are used. |
| `GOKUL_IDENTITY_PROVIDER_ABUSE_CONTROLS_VERIFIED` | Keep `false` until MSG91 send/resend/cooldown/expiry protections and DEV behavior are actually checked; then set `true`. The OTP availability endpoint remains disabled while false. |
| `GOKUL_CONSENT_POLICY_VERSION` | Legally reviewed nonempty policy identifier; verify optional-purpose processing and withdrawal before enabling real customer traffic. |
| `GOKUL_CHECKOUT_QUOTE_SIGNING_KEY` | Long random private signing secret. Delivery quote verification requires it. |
| `STAFF_MFA_ENCRYPTION_KEY` | Stable base64-encoded 32-byte secret; back it up securely. Rotating it without migrating enrolled staff MFA records makes their codes unusable. |
| `PRINT_AGENT_API_KEY` | Only when testing the external print agent: configure the same random DEV-only value in the agent and backend. |
| `DELIVERY_ECONOMICS_VERSION`, `DELIVERY_ECONOMICS_COST_VALID_UNTIL`, `DELIVERY_ECONOMICS_*` | Finance-approved nonzero cost inputs, INR fee floor/cap, contribution threshold, and inclusive IST validity date. Default numeric values are zero and **are not approved prices**. |

For the documented DEV setup, use `GOKUL_PUBLIC_API_ORIGIN=https://api-dev.gokulsweets.in`, `GOKUL_ALLOWED_ORIGINS=https://dev.gokulsweets.in`, `PHONEPE_REDIRECT_URL=https://dev.gokulsweets.in/checkout`, and `PHONEPE_WEBHOOK_URL=https://api-dev.gokulsweets.in/api/payments/webhooks/phonepe` **only if those domains are actually configured for your deployments**. Do not enter a Vercel preview hostname into a hard-coded DEV origin check; it will be rejected.

If only PhonePe is configured, set `PAYMENT_ENABLED_PROVIDERS=PHONEPE` and `PAYMENT_DEFAULT_PROVIDER=PHONEPE`. Configure any other enabled payment providers with their own DEV credentials. Obtain the PhonePe test client credentials and create an HMAC webhook in the PhonePe Business dashboard's Test Mode; use the webhook's ID and secret for `PHONEPE_WEBHOOK_CHECKSUM_KEY_ID` and `PHONEPE_WEBHOOK_CHECKSUM_SECRET`. For R2, create a separate DEV bucket, enable its development public URL, and create a bucket-scoped read/write R2 token. For MSG91, create an OTP Widget, obtain its server Authkey separately, and verify provider send/resend/cooldown/expiry controls. Obtain the DEV PostgreSQL connection settings from your database host. None of these provider-issued values can be generated by this application.

`GOKUL_IDENTITY_RATE_LIMIT_KEY` and `GOKUL_CHECKOUT_QUOTE_SIGNING_KEY` are independent random strings of at least 32 characters; `STAFF_MFA_ENCRYPTION_KEY` must be base64 of **exactly 32 random bytes**. Generate them separately and store only in the backend secret environment, never here or in frontend variables. `GOKUL_CONSENT_POLICY_VERSION` is a label for an actually reviewed policy, not a substitute for policy approval. `DELIVERY_ECONOMICS_VERSION` labels finance-approved inputs. The ten numeric `DELIVERY_ECONOMICS_*` inputs must be chosen by finance/operations from DEV pilot assumptions: food and payment rates are fractions, other amounts INR. The default zero costs are not approved prices.

### Frontend build on Vercel

Set these in the **DEV Vercel project environment before the manual frontend build**, never on Render. `NEXT_PUBLIC_*` values are compiled into the browser build, so a change requires another manual Vercel deployment.

| Variable | DEV value / origin |
| --- | --- |
| `NEXT_PUBLIC_API_URL` | `https://api-dev.gokulsweets.in` when that is the actual DEV backend origin; required for production-mode frontend builds. |
| `NEXT_PUBLIC_API_BASE_URL` | The same origin as `NEXT_PUBLIC_API_URL` or unset. If both are present and differ, the frontend build fails. |
| `NEXT_PUBLIC_MSG91_WIDGET_ID`, `NEXT_PUBLIC_MSG91_WIDGET_TOKEN` | From the MSG91 OTP Widget configuration. These are the widget's browser settings, never the server Authkey. Without either, customer SMS verification is hidden. |
| `NEXT_PUBLIC_IST_TIME_FIX_ENABLED` | `true` or unset; keep aligned with the backend's enabled IST fix. |
| `NEXT_PUBLIC_USE_MOCK_MENU` | `false` or unset. Setting `true` replaces live menu API data with mock products and invalidates DEV checkout tests. |

The customer widget appears only when its browser values are built in **and** `GET /api/storefront/customer-identity` responds `{ "enabled": true }`. That readiness endpoint also needs the MSG91 server Authkey, an identity rate-limit key, environment isolation and strict CORS, and verified provider abuse controls. If Render terminates TLS at a reverse proxy, configure `GOKUL_IDENTITY_TRUSTED_PROXY_CIDRS` only with the actual directly connected proxy CIDRs and verify the overwritten `X-Forwarded-*` headers; an empty value ignores forwarding headers and may block OTP exchange. Never guess proxy ranges.

## Prepare and test

Configure reviewed branch delivery polygons, zone and eligible products, stock, rider capacity/windows, and pilot roster before using customer delivery. Verify `GET /api/storefront/features` and `GET /api/storefront/customer-identity` after deployment; a configured flag alone does not establish provider or business readiness. Run the Sprint 2 pickup and campaign regression and the SCRUM-29/30/31/32/36/49 delivery, consent, OTP, payment, economics, dispatch, IST and staff-session scenarios. Record flag OFF and ON evidence in the Jira QA subtasks. For rollback, set affected `GOKUL_FEATURES_*` variables to `false` and redeploy; preserve paid orders and reconcile holds instead of deleting records.
