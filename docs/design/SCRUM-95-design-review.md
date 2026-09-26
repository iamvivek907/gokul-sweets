# SCRUM-95 · Pickup and checkout owner review

Open [the connected prototype](./scrum-95-checkout-prototype.html). Switch between a 400 px phone and desktop; use the regular, slot conflict and payment return scenarios. This is an interactive design example, not a checkout connected to the backend or PhonePe. Item prices and GST shown in it are illustrative.

## Decisions to approve or correct

| Decision | Proposed treatment |
| --- | --- |
| Brand | Carry forward the SCRUM-24 proposed cream, wine and gold direction, pending owner approval and real brand assets. |
| Pickup | Show shop, IST day and available time in one selector; offer **Change** in checkout without sending the customer home. Changing a shop with unavailable items requires an explicit decision. |
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
