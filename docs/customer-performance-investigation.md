# Menu and checkout performance investigation

The October 9 DEV logs show an idle menu repeatedly checking availability, then a one-item order taking 4.3 seconds and payment initialisation taking 4.8 seconds. The latter includes 3.6 seconds inside PhonePe token acquisition. Container samples rise to 491 MiB. These observations identify slow requests; they do not establish a safe production customer count or prove a memory leak.

## Changes

- Pickup previews now load service rules once per request, rather than once for item validation and again for slot evaluation. Manual/sold-out/dependency checks run first; the same request-local evaluator checks the dated slots. Later requests read fresh rules. Stock reads, reservation locking, pricing and checkout acceptance remain authoritative.
- Dated item summaries index the issues of open slots once. Previously each product repeatedly scanned the issue lists of every slot. Preserve request order, stock failures, first applicable reason, normal/priority capacity and pickup-window exclusions. The frontend also indexes the selected slot's issues.
- Menu catalog and advisory availability caches use 64 bounded lock stripes for rebuild coordination. Database work no longer holds the global LRU lock. Valid cache hits also bypass rebuild coordination, so independent branch/revision reads can run concurrently even for the same branch. Cold misses still rebuild under the branch stripe and revalidate before publication. Branches sharing a stripe still coordinate; the LRU retains its existing limits. Revision checks, write-transaction cache bypass and the one-second/boundary advisory expiry remain intact.
- Notification bell and sound runtime share overlapping inbox requests. No settled private result is retained. Identity changes, including cross-tab invalidations, abort obsolete reads. Cancelling one subscriber preserves other subscribers; cancelling the last aborts the request. The bell reads the authenticated inbox directly rather than checking identity first. Guest/disabled polling pauses after 401/403/404, and retries on identity changes or visible app resumption. Sound settings and inbox reads run concurrently.
- Successful new orders log stage durations for identity, idempotency claim, validation, commitment preflight, pricing, slot reservation, persistence, ownership/rewards, inventory and linking. These timings exclude final response conversion and transaction commit. The HTTP/transaction metrics are still needed to attribute remaining end-to-end time.
- PhonePe logs token-refresh network duration separately from lock waiting. Its existing token cache/refresh lock is retained. The configured `PHONEPE_TOKEN_EXPIRY_SAFETY_SECONDS` now controls reuse; previously the client always used 30 seconds. The default remains 30. Additional diagnostic logging failures cannot replace checkout or verification outcomes.

## Verification and limits

The summary regression compares the indexed implementation with the previous algorithm over 400 seeded scenarios. A local CPU-only comparison of 100 items (the endpoint batch limit), 40 slots and 20 summaries measured about 42 ms for the original implementation and 10 ms for the indexed implementation. An exploratory 500-item helper comparison also improved, but it is not a single real API request: the browser splits menus into 100-item batches. Neither comparison predicts total customer latency or capacity.

Concurrency tests hold one branch rebuild while another completes, and verify that callers for the same branch share the rebuilt snapshot. Pickup validation tests verify one evaluator build for manual checks plus 40 slot evaluations, fresh loading on a later request, and rejection of sold-out products. PhonePe tests cover concurrent token reuse, the configured safety window and exact UTF-8 HMAC payload verification. Inbox tests cover overlapping reads, cancellation, account/read invalidation, guest polling pause/resume, hidden/offline tabs and unmounts.

The disposable localhost Docker workload now also exercises 100 and 500 concurrent full-menu previews. Its 379-item fixture requires four sequential POSTs per preview, matching frontend batching. In the `full-menu-selected-date-preview` report, `requests` counts complete previews, each comprising four HTTP requests; p95 is the full preview latency. Existing mixed customer/staff, 31-day discovery, 512 MiB container and reservation regressions remain in CI. This is a synchronized burst with synthetic data, not a complete payment/browser soak test or a guarantee for the production plan.

## PhonePe callback investigation

The attached payment log rejected a callback with `INVALID_PHONEPE_WEBHOOK_SIGNATURE`, then confirmed payment through the status-refresh path. This particular rejection occurs after the configured key ID matches; it is not a missing-header or wrong-key-ID error. The controller already verifies the exact raw request bytes before parsing JSON.

[PhonePe's current webhook documentation](https://developer.phonepe.com/payment-gateway/website-integration/standard-checkout/api-integration/api-reference/webhook) describes HMAC and SHA authentication as separate dashboard choices. This application uses HMAC. A genuine provider callback and deployment configuration are needed to establish the reported mismatch; local signing tests cannot certify provider interoperability or the deployed secret.

For the DEV webhook, verify that the dashboard environment, webhook ID and generated secret match `PHONEPE_WEBHOOK_CHECKSUM_KEY_ID` and `PHONEPE_WEBHOOK_CHECKSUM_SECRET`. Do not substitute API client secrets or SHA username/password credentials. Preserve the raw UTF-8 body through any proxy; never decode/reformat JSON before verification. New rejection diagnostics record only body length, signature length and whether the signature has the expected hexadecimal shape. They do not record secrets, signature contents, expected hashes or payment payloads. Keep verification enabled and confirm a genuine callback succeeds; replay it to check idempotency.

## Measure on the intended deployment

After deploying through the normal reviewed process, compare the same branch/catalog and request mix before and after. Use an isolated environment with production-equivalent resource limits and a sandbox/mock payment provider.

1. Leave visible menus open for a sustained run at 100, then 500 simulated visitors, with realistic staggered entry. Include all feature and notification settings. Also test a simultaneous-opening burst, hidden tabs and returning online.
2. Include 100-item batches, a larger multi-batch menu, several pickup slots, unavailable service windows, stock not ready and actual carts. Record p50/p95/p99, timeout/error rate and complete-preview latency.
3. Mix browsing with checkout, payment return/status refresh and authenticated staff traffic. Use the new stage logs to choose query optimisations; do not remove validation or locks to obtain shorter timings.
4. Collect Hikari active/pending/timeout metrics, query count/duration, CPU, GC and total container memory. The default pool is five connections, but deployment overrides must be checked. The reported 491 MiB sample leaves little headroom if the limit is 512 MiB.
5. Recheck last-item and last-slot races, idempotent retries, payment callbacks and inventory commitments. Verify that changing service rules/stock or closing a branch still prevents invalid checkout.

The menu's branch, service-boundary, offers, settings and dated-availability freshness intervals remain unchanged. This PR reduces work per refresh and notification requests; it does not remove all idle menu traffic or establish a customer capacity limit. A smaller selected-slot preview contract or revision-aware public read model can be evaluated after the measurements above, with strict cart/checkout checks retained.
