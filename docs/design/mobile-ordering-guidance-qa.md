# Mobile ordering guidance QA

Scope: phones below 641px with the existing effective customer shell. Compact checkout still requires simplifiedCheckout, checkoutExperienceV2 and acceptedCheckoutQuote. Menu discovery opts into the server’s menuPreview availability mode; checkout retains whole-cart validation. No deployment or manifest changes.

- At 320, 390 and 640px, verify readable branch addresses and collection actions without horizontal overflow. At 641px and with flags OFF, verify existing layouts.
- Reopen a saved cart with a past IST pickup. Menu hides that selection, explains it and exposes Choose time. Confirm a future time, including a partial menu availability case. Server rejection must retain the previous stored pickup.
- Select a time that fits one unchosen menu product but cannot fulfil the actual cart quantity/weight. The sheet identifies the conflict and preserves both cart and prior pickup. Change the cart during confirmation and verify the stale response cannot save a pickup.
- With smart availability OFF, and separately with a date saved without a slot, verify a completed-order favourite with no dated stock is hidden. Available favourites remain optional; failed stock checks do not block the menu or checkout.
- Stall payment cancellation beyond 15 seconds. Stay and Retry recover; the reservation is retained locally and the UI explains that the server outcome is uncertain. Retry recovers the latest server status without creating another order/payment.
- Add a piece or weighted product. Check selected portion amount, actual published ratings, contextual pairings and completed paid visit favourites. Sold-out items must not appear as suggestions. Changing cart, branch or pickup during an add check must prevent that stale addition.
- Tap Pay now signed out, then signed in without branch consent: each tap highlights/focuses the requirement and creates no order. Check coins, offer sheet, bill sheet, Close, outside dismissal and Escape.
- Return from PhonePe to compact checkout: an existing gateway-marked reservation resumes its payment page. Follow an in-app link or Back: failed provider verification retains reservation; verified unpaid cancellation keeps cart and proceeds; paid results preserve the order. Repeat with desktop, flags OFF and delivery recovery.
- Test an actual low-network Android and iPhone gateway close/return. Chromium mocks verify recovery state and navigation but cannot reproduce PhonePe UAT or an iOS gesture rendering defect. Connection-aware savings depend on the browser exposing connection information.

Automated coverage: mobile-ordering-guidance.browser.mjs, mobile-single-checkout.browser.mjs, mobile-orders.browser.mjs, rewards-checkout.browser.mjs, menuPickupOptions.test.cjs and menuPrefetch.test.cjs, mobile-ordering-resilience.browser.mjs and CartAvailabilityServiceTest, alongside the complete existing browser suite.

Review regressions: mix a today-only product with a seven-day product and verify later dates remain discoverable while checkout still rejects the today-only item. Keep pickup add-ons ON with smart availability OFF; pairings and the existing add check must work without calling the disabled endpoint. Hold an offer response: verified pairings must appear first. Leave during a delayed Add: the saved cart must remain unchanged. Switch away from ordinary checkout and return: its scroll position must remain unchanged.

On slow/save-data phones, optional launch route/menu/cover prefetches, eager menu image warming, animated campaign playback and completed-order history fetches are skipped. Optional offer checking runs independently of product suggestions. Obsolete checks are aborted; pickup and Add checks have bounded waits and preserve the saved cart on failure. Browsers without Network Information support retain existing media behaviour, so actual device/network QA remains necessary.

Second review regressions:

- Stall Check Payment Status, then Back to menu. Stay/Escape can dismiss the waiting leave dialog before cancellation starts; the refresh deadline releases its controls and a subsequent exit safely checks/cancels.
- Stall a checkout add-on for 15 seconds: cart/payment controls recover and Retry is available. Navigate away before the response: a late response cannot mutate the cart. Repeat with changed cart/branch/pickup context.
- Fail menu availability: customer-rated favourites expose Retry availability, retain disabled unverified additions, and restore Add after a successful retry.
- Dispatch an identity change while personalized suggestions are visible and replacement requests are delayed: old favourites and offer results disappear immediately.
- Delay optional offer responses and assert the following product and floating Continue action retain their positions. The compact suggestion summary and offer-action space remain stable while requests settle. Optional additions open an explicit bottom sheet; longer content scrolls there without trapping menu gestures.
- Connection-policy mocks and paced API responses (150 ms per response) cover recovery behaviour. These do not reproduce actual cellular throughput/packet loss, native PhonePe behaviour or measured real-device frame rates.

Third review regressions:

- Stall the quote after successful add-on validation: the 15-second refresh deadline releases checkout and restores the unchanged prior cart. Stall the reservation PUT after the server applies it: retain the addition, report the uncertain outcome, block payment, and recover through Recheck current cart and total without creating a payment.
- Delay an offer with a ₹25 additional-spend target for ₹30/₹35 suggestions. Both x/y coordinates and product identity must remain stable. Late history candidates append without moving existing suggestions; updated prices refresh in place.
- Log out in another same-origin tab. The open suggestion sheet closes immediately and private favourites/offers disappear. Re-enter the menu tab after session changes with storage unavailable: focus/visibility resume must revalidate identity. Invalidation tokens contain no customer data.
- At 320/390/640px, check compact loading/empty summaries, bottom-sheet open/Close/Escape/outside dismissal, focus restoration, keyboard operation and long text. The menu has no nested vertical scroll trap; sheet contents and footer actions remain reachable. Repeat with desktop and flags OFF.

Fourth review regressions:

- Stall a manual offer POST after the reservation exists and the server commits the discount. After 15 seconds, release the loading state, retain cart/order, and require Recheck reserved total. Reload and enter the payment URL directly: no new order/payment may bypass recovery. Recover only the authoritative reserved total, then require acknowledgement before payment. Repeat with failed optional offer discovery and the older feature-OFF offers page.
- Delay a standard review add-on quote, then change cart, branch, pickup, contact, or pending order. Discard the response and never display its amount for the changed checkout. Unchanged checkout responses remain usable.
- Delay ratings and availability independently. Customer favourites keep the same compact summary space, with no movement of pickup or product controls. Open the explicit favourites sheet, verify available Add controls, retry failed availability, and check Close/Escape/outside dismissal and focus restoration at 320/390/640px.
