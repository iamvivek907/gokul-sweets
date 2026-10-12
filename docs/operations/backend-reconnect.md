# Backend health schedule and reconnect QA

## cron-job.org configuration

The existing public endpoint https://api-dev.gokulsweets.in/actuator/health returned HTTP 200 with status UP on 1 October 2026. It is already permitted by backend security.

- Title: Gokul backend health check.
- URL: https://api-dev.gokulsweets.in/actuator/health
- Enable job: on. Save responses: off unless debugging.
- Schedule: every 5 minutes, rather than the default 15.
- Advanced: GET, no body, authentication or secret headers.
- Notifications: failure and recovery; use consecutive-failure settings to avoid repeated alerts from a single cold-start timeout.
- Save, test execution, and inspect history for HTTP 200. The first cold start may exceed the scheduler timeout; wait for startup and test again.

External requests reduce idle gaps, but cannot guarantee uptime through maintenance, restarts, missed schedules or quota exhaustion. Keeping the service awake consumes Render free instance hours. No external job was created by this PR, and no merge/deployment occurred. Both Vercel Git deployment settings remain false.

## Customer behaviour

A first-time homepage request shows a frontend-bundled welcome with existing sweets artwork. After 2.5 seconds it presents “Online ordering is taking longer to connect.” with static pickup guidance and existing About policy links. Explore works immediately. No indefinite blocking animation, countdown, invented availability, prices, people or promotions.

Settings requests time out after five seconds. Failed checks retry approximately every 15 seconds while visible/online; healthy checks return to once per minute. Focus, visibility and online events resume checks. Consumers share one request and timer. Live settings restore the homepage automatically. Background outages retain flags and mounted journeys with a non-blocking notice. Cart, pickup and payment storage stay untouched. Last-known settings do not replace authoritative checkout validation. No mutation or payment is automatically retried.

## Startup investigation

Supplied logs show successful starts around 267–282 seconds, distributed across context creation, JPA and subsequent bean initialization. They do not prove one root cause. Repository inspection found no explicit startup sleep; R2 constructs a client with static credentials without an explicit storage request. Flyway and Hibernate validation remain enabled.

For a future manually approved diagnostic deployment, set GOKUL_STARTUP_DIAGNOSTICS_ENABLED=true. It buffers at most 4096 startup steps, logs the slowest 20 after ApplicationReadyEvent and drains the buffer. Logs contain step/bean names and duration, not general tag values, credentials or connection strings. No public startup endpoint is added. Nested durations overlap: do not sum them. Disable after collecting a cold start. Compare repeated timings, DB region/latency, Render CPU/memory charts and container/JVM limits before changing initialization. This PR does not claim faster startup.

## QA

1. Fresh desktop and 390px mobile session: fail/delay GET /api/storefront/features. Confirm branded welcome immediately, reconnect copy after 2.5 seconds, usable Explore and policy links, no old raw error page or horizontal overflow.
2. Keep backend unavailable for five minutes. Confirm bounded retries and useful content; hide tab and verify requests stop. Restore/focus and confirm automatic live homepage recovery without page reload.
3. With healthy settings, type into checkout/profile fields and retain a cart. Fail settings refresh. Confirm fields/cart remain and connection notice appears; recovery dismisses it without losing input.
4. Verify inventory, quote, slot and payment flows retain authoritative checks. Reconnect must never submit an order or payment.
5. Test browser offline/online, keyboard focus and reduced motion; welcome pulse stops with reduced motion.
6. Verify cron history GET requests return 200. Correlate Render Events with shutdowns to distinguish idle, deploy and maintenance.
