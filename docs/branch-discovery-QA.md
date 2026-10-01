# Branch discovery and in-place checkout QA

## Branch content and reviews

1. In Admin → Branches, select a permitted branch. Configure offering title/description cards, reorder, save draft. The customer branch home must still show only the previous publication.
2. Publish offerings and reload that branch home. Verify the configured order, no draft content and graceful empty states. Publish an empty draft to remove offerings.
3. Open two admin sessions. Save in one, then save the stale second session. Confirm a conflict without overwriting the first session or discarding the second session’s typed edits.
4. Verify staff without BRANCH_MANAGE or branch access cannot read/change the private draft API.
5. Verify branch rating uses published overall experience reviews of completed orders. Top three available normal-menu products use item ratings, with review counts. Hidden reviews, unfinished orders and other branches must not affect the score. Quotes are explicitly whole-order experiences; customer identities and contact-bearing comments are excluded. No fabricated ratings or testimonials.

## Menu and checkout (desktop and mobile)

1. On Menu → All, verify separate category sections in backend category order; search/filter results remain within their category. Verify the flag-OFF grid remains usable.
2. Make a normal pickup cart, check its backend price, accept it and find offers. Add-on cards must appear beside the saving/slab information, before the code form. Prices include item tax. No auto-add, fabricated discounts or promises of profitability.
3. On a 390px mobile viewport, verify the compact final-total/action bar remains fixed above navigation while cards scroll. Confirm no horizontal page overflow or obstructed bottom controls.
4. Reserve a large quantity (e.g. 40 pieces where only 10 unreserved remain). Add one suggested item. The check must include this unpaid order’s own reservation, preserve its order number and original expiry, refresh its backend quote and available offers, and require review of the updated total before payment.
5. Simulate stock/quote rejection or a provider/network outage during an addition. Remain on the same checkout screen with the existing cart/reservation. Retry or choose “Adjust quantities or pickup here”. No duplicate order/payment attempt and no navigation back to Cart.
6. Open “Adjust quantities or pickup”. Edit pieces/gram weights or explicitly select another current pickup date/time. Invalid quantities, malformed/past/out-of-window dates and elapsed times must be rejected. Availability and quote checks happen before saving changes. Failed checks retain the existing reservation and show a retryable error inside the dialog.
7. Confirm successful changes atomically update the same unpaid order, clear stale offer calculations and require total review. Never silently choose another slot. Cancel the dialog to keep the current pickup.
8. Verify priority/delivery/occasion ordering and all payment, cancellation, notification and paid-order behavior remain intact.

## Checks

Frontend lint, TypeScript, full Node regression suite in UTC and America/Los_Angeles, production build, desktop/mobile browser regression. Java 21 tests/build and PostgreSQL/Flyway validation run in CI, including the branch publication, privacy, rating and authorization tests. Vercel automatic Git deployment remains disabled; this PR does not merge or deploy.
