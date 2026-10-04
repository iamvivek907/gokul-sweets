# Mobile ordering guidance QA

Scope: phones below 641px with the existing effective customer shell. Compact checkout still requires simplifiedCheckout, checkoutExperienceV2 and acceptedCheckoutQuote. No backend, deployment or manifest changes.

- At 320, 390 and 640px, verify readable branch addresses and collection actions without horizontal overflow. At 641px and with flags OFF, verify existing layouts.
- Reopen a saved cart with a past IST pickup. Menu hides that selection, explains it and exposes Choose time. Confirm a future time, including a partial menu availability case. Server rejection must retain the previous stored pickup.
- Add a piece or weighted product. Check selected portion amount, actual published ratings, contextual pairings and completed paid visit favourites. Sold-out items must not appear as suggestions. Changing cart, branch or pickup during an add check must prevent that stale addition.
- Tap Pay now signed out, then signed in without branch consent: each tap highlights/focuses the requirement and creates no order. Check coins, offer sheet, bill sheet, Close, outside dismissal and Escape.
- Return from PhonePe to compact checkout: an existing gateway-marked reservation resumes its payment page. Follow an in-app link or Back: failed provider verification retains reservation; verified unpaid cancellation keeps cart and proceeds; paid results preserve the order. Repeat with desktop, flags OFF and delivery recovery.
- Test an actual low-network Android and iPhone gateway close/return. Chromium mocks verify recovery state and navigation but cannot reproduce PhonePe UAT or an iOS gesture rendering defect. Connection-aware savings depend on the browser exposing connection information.

Automated coverage: mobile-ordering-guidance.browser.mjs, mobile-single-checkout.browser.mjs, mobile-orders.browser.mjs, rewards-checkout.browser.mjs, menuPickupOptions.test.cjs and menuPrefetch.test.cjs, alongside the complete existing browser suite.
