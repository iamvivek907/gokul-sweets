# Runtime payment provider switching

Owners use **Admin → System → Payment providers** to activate PhonePe or Razorpay
for new payment attempts across all branches. The choice is stored in PostgreSQL,
read on every new attempt, and survives restarts. PAYMENT_DEFAULT_PROVIDER is the
bootstrap route only until an owner saves a switch. PAYMENT_ENABLED_PROVIDERS
allows configured adapters to become selectable; set PHONEPE,RAZORPAY for both.
Paytm remains available for historical processing but is not offered as a runtime
choice until its complete production journey is approved. After saving Razorpay as
active, PhonePe may be removed from eligibility without changing the old bootstrap
default. With no saved route, a disabled bootstrap default rejects new payments;
there is no silent fallback. Confirm the active choice in Admin before retirement.

## Configuration and ownership

The screen exposes no credentials and checks configuration, not gateway uptime
or merchant approval. Missing credentials/webhook secrets, incorrect environment
routes, Razorpay test keys in PROD and non-production PhonePe endpoints block
activation. PhonePe also requires a valid effective base64 32-byte checkout encryption
key. `PAYMENT_CHECKOUT_ENCRYPTION_KEY` falls back to `STAFF_MFA_ENCRYPTION_KEY`
when unset; an explicitly empty override is invalid. Secrets remain in backend deployment settings. Each change requires
an active OWNER_ADMIN, the existing session/CSRF protection, a current revision
and a reason. Conflicting owner edits return 409. Switch and audit commit together;
history is bounded to twenty entries. If a response is lost, refresh to observe
state; never automatically retry an uncertain switch.

## Payment continuity

A new attempt uses the route read when that request reaches provider selection.
An attempt already initialized or pending keeps its original provider. A stale
browser preference cannot override Admin routing; the returned payment's provider
controls which checkout opens. Existing payments, webhooks, expiry checks and
refunds use the provider recorded on that payment even when it is no longer
eligible for new attempts. Retain original merchant credentials and webhooks
until historical obligations are resolved. Switching cannot transfer a charge or
refund between providers. There is no automatic failover.

The isolation guard still validates the database, media origin and exact DEV/PROD
CORS. PhonePe-specific checks apply when PhonePe is enabled. A Razorpay-only
deployment can start without PhonePe credentials.

## Provider setup

PhonePe needs API credentials/version, payment and OAuth origins, storefront
/checkout redirect base, backend /api/payments/webhooks/phonepe URL and webhook
HMAC credentials. PROD uses https://api.phonepe.com/apis/pg and
https://api.phonepe.com/apis/identity-manager.

Razorpay needs RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET and
https://api.razorpay.com as API origin. PROD requires a rzp_live_ key. Configure
capture in the merchant dashboard; the application only confirms captured
payments. Register https://<backend>/api/payments/webhooks/razorpay and subscribe
to payment.captured, payment.failed, refund.processed and refund.failed. Use
separate DEV/PROD credentials and webhook secrets.

Checkout signatures and raw-body webhook signatures are verified. Captured
webhooks validate local amount/currency. Refund callbacks match the recorded
refund ID, amount and currency. Lost initiation acknowledgement can recover only
when the callback carries the matching local receipt/reference. Unrelated manual
refunds do not complete the application's requested refund. Refund POST retries
reuse the same X-Refund-Idempotency key.

## DEV acceptance before promotion to main

1. Configure both providers; test owner access and activation blocking for missing
   credentials. Verify that readiness never includes secrets.
2. Pay with PhonePe; leave another attempt pending, switch to Razorpay, then
   resume the PhonePe attempt. Start a fresh attempt from a stale browser page;
   it must use Razorpay.
3. Verify captured success, failure, dismissal, refresh and browser closure before
   callback. Webhooks/polling must recover status; duplicate callbacks must not
   repeat confirmation, stock release or rewards.
4. Check capture after reservation expiry follows late-payment refund handling.
5. Cancel an eligible order; verify partial food refunds, pending/processed/failed
   transitions and customer-visible provider refund ID. Test unrelated refund
   callbacks and an interrupted initiation response.
6. Switch back to PhonePe; Razorpay refunds must still reconcile. Keep both
   merchants' credentials and callbacks available.
7. Test concurrent owners, interrupted Admin response, refresh recovery and
   persistence after restart.

Automated fixtures cover routing persistence/concurrency, isolation, checkout
continuity and refund callback correlation. They do not validate live merchant
capture configuration, bank processing or uptime. Complete merchant/device tests
in DEV before production promotion.

## Standard Checkout implementation

This is a Spring Boot/Java backend and Next.js storefront; the existing Java HTTP
adapter is used instead of introducing a Node.js SDK or duplicate endpoints.

- `POST /api/payments` accepts the business order reference, obtains the active
  provider and creates a server-priced attempt. Razorpay calls `POST /v1/orders`
  with amount in paise, INR and a receipt. Amounts below 100 paise are rejected
  before saving an attempt; client-submitted amounts are never trusted.
- The response includes `providerOrderId`, `amount`, `currency` and the public
  `checkoutKeyId`. The browser loads the official `checkout.js` and opens Standard
  Checkout; dismissal and `payment.failed` use the existing recovery flow.
- `POST /api/payments/{paymentId}/razorpay/verify` accepts the three Razorpay
  success fields. The server checks the locally recorded order and HMAC-SHA256
  signature, then verifies the gateway payment belongs to that order, matches
  the frozen amount and is captured. A signature alone does not mark it paid.
- `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` stay on the backend. A
  `NEXT_PUBLIC_RAZORPAY_KEY_ID` variable is unnecessary because the backend
  returns the public key ID. Never commit a credentials `.env` file.

After rotating any exposed test credentials, configure fresh backend environment
values, deploy DEV and activate Razorpay in Admin. Place an eligible order, pay
with Razorpay test mode, and confirm paid status. Also test a declined payment,
modal dismissal, invalid signature, webhook delivery and an eligible refund.
Enable automatic capture in the Razorpay dashboard and register the webhook
above. These merchant tests are required in addition to automated fixtures.
