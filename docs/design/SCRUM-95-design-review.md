# SCRUM-95 · Pickup and checkout owner review

Open [the connected prototype](../../frontend/public/design/scrum-95-checkout-prototype.html). On the PR preview deployment, open `/design/scrum-95-checkout-prototype.html` for an interactive walkthrough. Switch between a 400 px phone and desktop; use the regular, slot conflict and payment return scenarios. This is an interactive design example, not a checkout connected to the backend or PhonePe. Item prices and GST shown in it are illustrative.

## Decisions to approve or correct

| Decision | Proposed treatment |
| --- | --- |
| Brand | Explore a futuristic, luminous wine and gold direction with layered depth, restrained glow and strong contrast. This is an owner-review variant pending SCRUM-24 approval and real brand assets. |
| Pickup | Use a compact, prominent branch selector in the header showing the active shop, plus the same shop in the pickup context; show IST day and available time in one selector; offer **Change** in checkout without sending the customer home. Changing a shop with unavailable items requires an explicit decision. |
| Conflict | Keep cart and contact details; show fresh alternatives in the same context. Choosing one refreshes the amount. |
| Pricing | One line per item with piece/weight and unit price, followed by item subtotal, GST, pickup charge, savings, and one payable amount. Values come from a fresh backend response in the implementation. |
| Offers | Place eligible offers within the review step, with the payable amount updated before explicit payment. |
| Payment return | After the payment gateway was opened, show a distinct unknown/checking state and an order record action; never equate a provider timeout with failure or create a second charge automatically. |
| Voice | Short customer language: “Choose pickup,” “Have a final look,” “Confirm total.” Keep cancellation disclosure and IST pickup promise visible. |

## Tap-count hypothesis

Counts start with a nonempty cart, a selected shop and no saved pickup time, end when the customer chooses to open the payment provider, and exclude product adding, typing, optional offers and OTP. They count explicit taps, not automatic checks or network requests.

| Path | Current implementation | Proposed prototype | How counted |
| --- | ---: | ---: | --- |
| Normal, first visit | 9 | 5 | Current: Cart continue; day; time; pickup continue; customer continue; check final price; accept price/reserve; offers continue; pay. Proposed: choose time (day defaults to today); continue; review details; confirm amount; open provider. |
| Returning, valid saved details and time | 5–7 | 2–3 | Depends on whether a saved reservation can be reused and the price is unchanged. Payment always requires an explicit tap. |
| One expired-slot recovery | Normal path + 3–5 | Normal path + 2 | Current estimate includes backtracking to pickup and returning through the affected review step; proposed: open alternatives, select a fresh time, then explicitly accept any changed amount. |

These are *design targets*. The acceptance test should count actual taps from the deployed DEV flow before/after; never silently skip explicit approval for a changed price, branch, reservation or payment. The server remains authoritative for inventory, prices, pickup capacity and payment status.

## Implementation boundary after approval

1. Add one centrally documented flag, default OFF, covering the new presentation; retain the old route/UI when OFF.
2. Reuse existing cart switch preview, pickup availability, quote, pending order and payment reconciliation APIs. Keep the quote/reservation validation and gateway initiation separate.
3. Remove duplicate pickup cards and repeated unchanged checkout decisions only when server state permits it. Keep price-change acknowledgment explicit.
4. Verify DEV in both flag states on phone, desktop, 200% zoom and keyboard, including expired slot, branch inventory conflict, stale quote, slow/offline request, closed browser and payment return.

This story asks for owner review **before implementation**. SCRUM-24 visual direction still awaits owner approval; do not treat this prototype as approval or mark SCRUM-95 Done.

## Owner review gate

Please review the prototype on the PR preview at `/design/scrum-95-checkout-prototype.html` and record approval or corrections for: shop and pickup change, conflict recovery, order/tax hierarchy, offer placement, cancellation wording, and ambiguous payment return. The example date is 26 Sep 2026 IST; no live availability, stock, reservation, quote, order, payment, or customer data is used. The prototype includes a demonstration of a conflict and payment return, but the sold-out, expired quote, offline, saved pending order, and real provider cases require implementation and DEV QA after approval. SCRUM-24 brand approval is still pending.

## Visual feedback revision · 26 Sep 2026

The owner found the first draft too similar to a conventional website and the header shop change dated. The current prototype explores a darker, more distinctive editorial treatment: a luminous brand mark, layered wine surfaces, warm gold actions and selection states, and a compact header control that names the active pickup shop. The pickup panel echoes that shop without creating a second selection state. The visual direction remains a prototype, not final brand approval; test its legibility and contrast on the actual phone before implementation. Avoid glow on dense item totals, warnings or form copy.

## Implementation approval and status

On 26 Sep 2026 at about 18:08 IST, the owner approved the revised futuristic checkout direction in chat and asked to proceed. The PR now applies that direction to the actual checkout behind the default-OFF `checkoutExperienceV2` feature response. This approval covers the SCRUM-95 visual direction; it does not approve SCRUM-24's overall brand system or establish DEV payment/inventory QA. The existing quote, reservation and provider handlers remain the source of truth. An in-review time change refreshes cart availability and invalidates the previous quote before another commitment.
