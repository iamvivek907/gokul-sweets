# Sprint 2 and 3 manual DEV QA configuration

`application.properties` now defaults the additive Sprint 2/3 storefront, identity, delivery, economics, dispatch, and staff-session features to `true`. The customer theme flag is also explicit. Every flag still accepts a matching `GOKUL_FEATURES_*=false` setting in the backend environment as a rollback. Existing automatic pickup expiry remains disabled. This is a DEV test candidate: do not deploy the same defaults to production until QA is complete.

## Configure the DEV backend before deploying

Set these in the **backend server environment**, never in Git. Do not copy production provider credentials or customer data into DEV.

| Environment variable | Required value or review |
| --- | --- |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Separate DEV PostgreSQL database; Flyway V73–V75 must apply. |
| `GOKUL_DEPLOYMENT_ENVIRONMENT`, `GOKUL_PUBLIC_API_ORIGIN`, `GOKUL_ALLOWED_ORIGINS` | `DEV`, HTTPS backend origin without a path, and exactly `https://dev.gokulsweets.in`. Isolation and environment CORS default ON and reject inconsistent startup wiring. |
| `PHONEPE_REDIRECT_URL`, `PHONEPE_WEBHOOK_URL` | `https://dev.gokulsweets.in/checkout` and `<GOKUL_PUBLIC_API_ORIGIN>/api/payments/webhooks/phonepe`. |
| `PHONEPE_CLIENT_ID`, `PHONEPE_CLIENT_SECRET`, `PHONEPE_WEBHOOK_CHECKSUM_KEY_ID`, `PHONEPE_WEBHOOK_CHECKSUM_SECRET` | DEV or sandbox payment credentials and webhook credentials. Verify the configured base and authorization URLs match the provider environment. |
| `R2_BUCKET_NAME`, `R2_PUBLIC_URL`, `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | Dedicated DEV bucket (not the default shared bucket) and its HTTPS public URL and credentials. |
| `GOKUL_MSG91_SERVER_AUTHKEY`, `GOKUL_IDENTITY_RATE_LIMIT_KEY`, `GOKUL_IDENTITY_TRUSTED_PROXY_CIDRS` | Verified DEV SMS provider credential, random key of at least 32 characters, and only actual proxy CIDRs if forwarding headers are used. |
| `GOKUL_IDENTITY_PROVIDER_ABUSE_CONTROLS_VERIFIED` | Keep `false` until MSG91 send/resend/cooldown/expiry protections and DEV behavior are actually checked; then set `true`. The OTP availability endpoint remains disabled while false. |
| `GOKUL_CONSENT_POLICY_VERSION` | Legally reviewed nonempty policy identifier; verify optional-purpose processing and withdrawal before enabling real customer traffic. |
| `GOKUL_CHECKOUT_QUOTE_SIGNING_KEY` | Long random private signing secret. Delivery quote verification requires it. |
| `STAFF_MFA_ENCRYPTION_KEY` | Stable base64-encoded 32-byte secret; back it up securely. Rotating it without migrating enrolled staff MFA records makes their codes unusable. |
| `DELIVERY_ECONOMICS_VERSION`, `DELIVERY_ECONOMICS_COST_VALID_UNTIL`, `DELIVERY_ECONOMICS_*` | Finance-approved nonzero cost inputs, INR fee floor/cap, contribution threshold, and inclusive IST validity date. Default numeric values are zero and **are not approved prices**. |

If only PhonePe is configured, set `PAYMENT_ENABLED_PROVIDERS=PHONEPE`. Configure any other enabled payment providers with their environment-only credentials. Configure the print agent API key where an external agent is used. Confirm the frontend points to this DEV backend.

## Prepare and test

Configure reviewed branch delivery polygons, zone and eligible products, stock, rider capacity/windows, and pilot roster before using customer delivery. Verify `GET /api/storefront/features` and `GET /api/storefront/customer-identity` after deployment; a configured flag alone does not establish provider or business readiness. Run the Sprint 2 pickup and campaign regression and the SCRUM-29/30/31/32/36/49 delivery, consent, OTP, payment, economics, dispatch, IST and staff-session scenarios. Record flag OFF and ON evidence in the Jira QA subtasks. For rollback, set affected `GOKUL_FEATURES_*` variables to `false` and redeploy; preserve paid orders and reconcile holds instead of deleting records.
