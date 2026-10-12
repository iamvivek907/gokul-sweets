# PhonePe refund reconciliation

Eligible paid normal-order cancellations queue the food refund automatically. Keep `GOKUL_BACKGROUND_JOBS_ENABLED=true` on the backend that owns scheduled maintenance. Import worker and async-import flags do not control refunds.

New refunds persist a submission-attempt marker before calling PhonePe, then submit directly with the recorded merchant refund ID and amount. If submission acknowledgement is lost, recovery checks that same reference. Only an explicit refund-not-found response from the status GET permits another submission with the same reference. Missing fields, timeouts, authentication failures, generic 404s and other uncertain responses never authorize another POST.

Existing pending refunds are marked as potentially submitted during migration. This preserves safety for payments like 151: deployment does not assume an earlier submission failed just because the backend lacks its acknowledgement. If PhonePe continues returning an invalid response, sanitized operation, HTTP status, provider code and field-type diagnostics identify the response without logging response bodies or credentials. Verify that refund with PhonePe before any manual merchant-dashboard action; a dashboard refund may use a different reference.

## Polling and restart recovery

Due refunds are selected by ID in batches of ten. A DB claim lasts twenty minutes, covering the configured maximum request timeouts and OAuth/401 recovery, and is committed before any HTTP call. A crashed worker can recover after its claim expires. Successful pending responses are checked again after two minutes. Consecutive failures back off at 1, 2, 4, 8, 16, 32 and then 60 minutes. After eight failures, the admin order correction panel shows a staff-review notice; hourly reconciliation continues. Uncertainty remains `REFUND_PENDING`, rather than claiming the provider rejected or completed a refund. Valid outcomes reset the failure counter; confirmed terminal results clear the review flag.

This limits provider traffic and stack-trace noise. It does not guarantee normal HTTP payload latency or change Render CPU/RAM capacity.

## Webhook configuration

In the appropriate PhonePe Business Dashboard environment, edit the existing HMAC webhook to subscribe to `pg.refund.completed` and `pg.refund.failed` alongside the existing checkout events. Use the same HTTPS endpoint `/api/payments/webhooks/phonepe` and existing checksum key ID/secret environment configuration. No new secret is needed. Polling remains available when callbacks are delayed or missing.

Authenticated refund events are matched by merchant refund reference and provider, then checked against the original merchant order, exact refund amount and existing provider refund ID. Event and state must agree. Duplicate completion is idempotent, and late pending/failed updates cannot overwrite a completed refund. Unsupported events are acknowledged without payment-field parsing.

Reference: https://developer.phonepe.com/payment-gateway/website-integration/standard-checkout/api-integration/api-reference/refund
Webhook reference: https://developer.phonepe.com/payment-gateway/website-integration/standard-checkout/api-integration/api-reference/webhook

## QA

Use sandbox transactions only for end-to-end checks; the automated tests use mocks and local database fixtures and never send live refunds.

1. Cancel an eligible paid order. Confirm one queued refund, a persisted submission marker, and the same reference and food amount on submission.
2. Lose the submission response. On retry, return the existing refund from status; assert that no further POST is issued.
3. Return malformed JSON fields or timeouts. Verify the payment remains pending, retry time increases, and repeated failures show the staff-review notice.
4. Deliver signed completion/failure callbacks. Check amount/order/refund-ID mismatches and invalid signatures are rejected; replay completion and ensure notifications run once.
5. Run two schedulers against one due refund. Only one claims it. Expire a claim to simulate worker restart and verify recovery.
