# Cache verification, worker separation and menu ownership

## Observed production reads — 23 October 2026

The bounded read-only probe observed the production API at `https://api.gokulsweets.in`, branch 1. The [recorded evidence](../qa/architecture/production-menu-reads-20261023.json) contains status, body size and elapsed time only; no customer data or credentials.

- Public catalog returned `Cache-Control: max-age=31536000, public, immutable`: catalog caching is configured.
- `smartAvailability=true`; the selected-date `menu-state` endpoint returned 404. There was no preview cache-mode header on the existing deployment. Versioned dated-preview caching is **unverified**, not proven active or definitively disabled. An older backend deployment or an OFF flag can produce this outcome.
- Three identical seven-item advisory reads returned 200 and 15,763 bytes each, taking approximately 10.33, 7.52 and 10.34 seconds from this agent's network path. These include network/proxy latency; they are not customer page-load timings, database CPU measurements or evidence of a cache hit rate. No stock, cart, order, hold or payment was created.

Reproduce a small sample with:

```bash
python scripts/measure-menu-reads.py --origin https://api.gokulsweets.in --branch 1 --samples 3
```

The script caps CLI samples at five, products at ten, requests to one date, and delay to 2–30 seconds. POSTs use only the public advisory preview route. It does not exercise checkout or reservation endpoints. Failed or unavailable diagnostics remain unknown rather than falsely reporting cache disabled.

### Verify and activate the dated-preview cache

After deploying this PR, opted-in advisory responses carry `X-Gokul-Menu-Preview-Cache: enabled` or `disabled`. This reports the rollout setting, not whether that particular request reused an entry. The existing public state route returns 200 only with smart availability and versioned previews enabled.

1. Confirm V137 is applied and test ready stock, reservations, slot capacity, manual closures and time cutoffs in DEV.
2. Set backend `GOKUL_MENU_VERSIONED_PREVIEWS_ENABLED=true` in the deployment environment and restart. An explicit false remains the rollback; the source default stays OFF.
3. Rerun the probe: expect `menu-state` 200, `X-Gokul-Menu-Preview-Cache: enabled`, and no change to checkout authority.
4. Collect private Micrometer counter `gokul.menu.preview.lookups` with bounded tag `outcome=hit|miss` per replica; compare rates, database statement counts and HTTP latency. These count lookup attempts, including a later failed transaction, not delivered responses. No product, branch, customer or token values are metric tags. Public actuator exposure remains health/info only.
5. For browser request counts: clear Network, leave cache enabled, select Fetch/XHR, reload once and observe a visible unchanged menu for one minute; then change stock in DEV. Unchanged version probes should not cause full previews; changed stock and cutoff boundaries should refresh. Check a hidden tab and reconnect separately. The existing `menu-versioned-stock.browser.mjs` regression covers those request-count invariants and cart preservation.

Production environment values were not changed by this work. GitHub access does not grant access to Render environment configuration.

## PWA public-image storage

The service worker now limits the image cache to 120 entries, 24 MiB total response-body bytes and 2 MiB per image. Online freshness expires after seven days; a stale cached public image may be used on network failure so existing offline browsing remains usable. Oldest stored entries are evicted first. Unknown-length responses are read with a strict per-item cap; oversized and opaque responses are not retained.

Image and metadata writes are serialized, with at most four queued writes. Extra simultaneous loads render normally without being cached. Metadata and payload writes roll back together on failure; quota errors do not block network images. Network images can render before cache writeback finishes. API requests continue bypassing the service worker cache. Push deduplication remains in its separate existing cache. Activation upgrades cache version v2 → v3 and removes the prior unbounded image cache; the existing manifest identity and registration flow remain unchanged.

## Optional API / worker separation

The legacy `SchedulingConfig` and `InventoryConfiguration` independently enabled scheduling, bypassing the intended global switch. Scheduling registration now has one owner. `BackgroundSchedulingConfigurationTest` includes all three configurations so a future duplicate cannot silently bypass its tests.

| Setting | Existing combined deployment | API process after a controlled split | Worker process |
| --- | --- | --- | --- |
| `GOKUL_JOBS_ROLE` | `combined` (default) | `api` | `worker` |
| `GOKUL_SCHEDULED_JOBS_ENABLED` | `true` | `true` (role still prevents callbacks) | `true` |
| `GOKUL_JOBS_WORKER_ENABLED` | Existing setting | `false` | `true` for durable imports |
| `GOKUL_BACKGROUND_JOBS_ENABLED` | Existing production true | Existing setting | `true` |
| `GOKUL_IMPORTS_ASYNC_ENABLED` | Existing false | Enable only once the import worker is healthy | Match API settings |
| Database pool | Existing default 5 | Size from measured API demand | Separate bounded pool; include both pools in the database connection budget |

`api` prevents all scheduled callbacks, including inventory, payment expiry, refunds, reporting and notifications, from running in that process. `worker` and `combined` preserve all existing scheduled callbacks and their individual feature switches. An invalid active role fails startup. The explicit global OFF switch remains suitable for intentionally idle DEV; it now actually suppresses callbacks even with legacy configurations loaded.

Before deploying, confirm the production value of `GOKUL_SCHEDULED_JOBS_ENABLED` is true: this change fixes the old bypass, so an existing false value will now actually suppress every scheduled callback.

The default is unchanged: no additional service or connections are started automatically. Separate deployments give workers their own CPU, heap and connection pool. Worker role is not an authentication or network-isolation boundary: keep its HTTP deployment private/health-only at the hosting layer and retain the existing security configuration. Do not assume a non-web Spring process is compatible with this application's security configuration.

Measure scheduler duration, task/queue age, database acquisition wait, pending payment/refund age and customer p95 latency before paying for a separate process. For a split, validate the handoff in DEV, maintain a healthy owner of financial/reservation schedules and monitor lag. Do not switch the customer API to `api` while no worker is ready, and do not assume every scheduled job is safe for overlapping owners without testing its idempotency/lease path. Keep the same business flags, environment isolation, database and provider settings on both. Roll back to one healthy `combined` deployment if needed. This PR prepares the separation; it does not claim background contention has been measured or deploy new workers.

## Menu CSS and carousel

Pickup-card rules from seven stylesheet layers now have a single owner, `menu-pickup.css`. Their original cascade order, media scopes and feature selectors are preserved. The computed style fingerprints match the previous implementation at 320/390/640/1280px, including expired/selected pickup and feature-OFF. Other menu styles remain in their existing owners; this is an incremental consolidation, not a rewrite of all customer CSS.

The phone Diwali carousel uses one artwork surface, a stronger gradient behind text, editorial headline, category badge, cream action and integrated translucent controls. Published copy/media/crop and category destinations remain authoritative. Desktop and legacy banner presentation remain separate. Dot/arrow/keyboard navigation and reduced motion are retained. Rapid repeated arrow taps advance from the requested target; a programmed smooth scroll only activates its destination after arrival, avoiding mid-transition focus/selection races. Pointer/wheel interaction restores manual scrolling.

## Validation and rollout status

New image-cache tests cover expiry, offline fallback, entry/byte/item bounds, concurrent writes, quota and partial-metadata failures, upgrade cleanup and API bypass. Existing push/installation tests retain their contracts. Backend tests cover all scheduler configurations together, process roles, invalid configuration, cache-mode headers and bounded metric tags. The diagnostics script has read-only request-contract tests in CI.

Local checks and PR CI status are recorded in the PR description. No backend environment values, production content, inventory or worker deployments have been changed.
