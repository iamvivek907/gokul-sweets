# Intelligent promotional push

Admin → Storefront → Smart notifications configures each branch. This is separate from Offers and from order-status notifications. New branches and upgrades are disabled by default. After testing in DEV, deploy to PROD and explicitly enable the desired production branch.

## Behaviour-based intelligence

The engine evaluates lunch (12:00–13:30), snacks (16:00–18:00), and dinner (19:00–20:30), in Asia/Kolkata time. It considers only verified customers with a completed order within a year, using their most recent completed-order branch. A most-frequently-ordered available category over the last 90 days informs the topic: sweet fans receive a sweets variant; snack fans wait for the snack window. Other customers receive a meal-time message. This is explainable rule-based personalization, not an LLM and not a machine-learning model trained on click history.

Sixteen curated short message variants per language are built into the application. English or English titles with Hinglish bodies can be selected. Messages rotate deterministically per customer/day and avoid repeating the same template for 14 days. Copy never invents prices, discounts, freshly-prepared claims or stock quantities. Existing published offers are not advertised automatically by this release. Live product service hours and sellable inventory remain authoritative on the menu and at checkout; the engine checks branch opening hours and active/available menu entries, not an inventory reservation.

No customer phone numbers, item lists or order details appear in push copy. No external AI provider, API key or customer-data export is required.

## Eligibility and delivery

- Require current-policy MARKETING consent, offer notifications enabled in the customer's inbox settings, and an active registered push device with a live verified session.
- Respect customer quiet hours. Suppress customers with a non-failed/non-cancelled order in the past 24 hours or an active confirmed/preparing/ready order, including future pickups.
- Reserve no more than one promotion per India-time day across all branches, and 1–3 per rolling seven days as selected in admin. Queued/uncertain/skipped attempts count conservatively against limits.
- Recheck branch pause, opening hours, current consent, inbox preference, session, quiet hours and new purchases immediately before every network attempt.
- Use the most recently registered live device. Push delivery uses an independent leased queue, retries transient failures at most three times, revokes gone endpoints, and deduplicates stable marketing IDs in the service worker.
- Expire messages after 15 minutes and use the existing transport's 60-second push TTL. Global pending capacity is 25 messages, with at most five reservations per branch evaluation and five network attempts per tick. Excess eligible customers remain eligible for later ticks; expired queue entries are skipped in bounded batches.
- Retain pseudonymous delivery history for 90 days; cleanup removes at most 100 terminal records per tick. Order notifications and their acknowledgement behaviour are unchanged.

Admin's “Evaluate eligible customers now” runs the same rules as the scheduler. It is not a force-send or broadcast bypass. Customers without purchase history are deliberately not assigned an inferred branch. Off-window evaluations queue nothing.

“ACCEPTED” records push-provider acceptance, not device display or a customer opening the message. Tapping a promotion opens its exact local branch page; it does not silently change a cart's branch.

## Configuration and rollout

1. Existing Web Push VAPID keys and notification feature prerequisites must be correctly configured. Existing environment-isolation and consent-policy checks remain mandatory.
2. `GOKUL_MARKETING_PUSH_ENABLED` is the master emergency switch (default true); per-branch enabled settings still default false. Setting it false prevents scheduling and sending.
3. The existing `gokul.notifications.web-push.scheduler-enabled` switch also controls automatic execution. Dedicated import workers do not run the marketing scheduler.
4. In DEV, enable a test branch, opt in a test account, register push, and use a completed-order fixture. Verify foreground/background/closed-PWA delivery, permission revocation, pause and quiet hours on Android. iPhone Web Push requires a supported installed Home Screen web app.
5. Choose a branch language, weekly limit and topics; save. Review message previews. Activate PROD only after delivery tests and CI pass.

Admin endpoints require REBATE_MANAGE and access to the requested branch. Settings use optimistic versions. History contains no customer identifiers, subscriptions or secrets. No campaign is activated and no customer receives a message merely by applying the migration.
