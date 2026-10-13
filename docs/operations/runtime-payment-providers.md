# Runtime payment provider switching

Owners use **Admin → System → Payment providers** to activate PhonePe or Razorpay
for new payment attempts across all branches. The choice is stored in PostgreSQL,
read on every new attempt, and survives restarts. PAYMENT_DEFAULT_PROVIDER is the
bootstrap route only until an owner saves a switch. PAYMENT_ENABLED_PROVIDERS
allows configured adapters to become selectable; set PHONEPE,RAZORPAY for both.
Paytm remains available for historical processing but is not offered as a runtime
choice until its complete production journey is approved.

## Configuration and ownership

The screen exposes no credentials and checks configuration, not gateway uptime
or merchant approval. Missing credentials/webhook secrets, incorrect environment
routes, Razorpay test keys in PROD and non-production PhonePe endpoints block
activation. Secrets remain in backend deployment settings. Each change requires
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
