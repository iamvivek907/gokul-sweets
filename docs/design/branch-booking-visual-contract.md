# Branch and booking visual contract

Owner direction, 26 September 2026. This carries the approved editorial direction from arrival into menu, pickup, planned orders and future venue bookings. The supplied restaurant detail and booking screenshots illustrate hierarchy and flow, not content, brand styling or capabilities to copy. The menu and pickup images shared in conversation are concepts, not shipped screens; sample branch, products, photos, dates and times are placeholders.

## Shared branch page

- Reuse Gokul's forest teal, ivory, apricot accents, clean sans-serif controls and restrained serif display headings. Keep one customer header, branch selector, footer and mobile navigation from the shared shell. Do not copy another brand's identity or ratings.
- Establish branch identity above the task: real name, verified address/contact/opening information, pickup capabilities and owner-approved branch photography. Show a gallery when media exists; otherwise use a deliberate branded fallback. Never present illustrative food photography as a real branch interior.
- Expose only working destinations: Overview, Menu, Pickup, then Occasions/Restaurant/Banquet when each capability actually exists. Preserve selected branch and explain the effect of a switch on a cart or booking draft.
- Give the menu prominent search, categories, actual piece/weight units, branch prices and item-level availability. Product photos come from real menu assets. Keep order summary available on mobile. The backend remains authoritative for price, stock and pickup slots.

## Distinct booking journeys

| Journey | Customer decisions | Commitment language |
| --- | --- | --- |
| Food pickup now or later | Branch, live cart, service date and available pickup time, then review and payment | A displayed time is not reserved until server commitment. Show fresh alternatives and preserve the cart on conflict. |
| Planned bulk pickup for an event | Occasion, date, quantity, lead time, branch and production feasibility | Separate enquiry, estimate, manager-approved quote, hold, payment and confirmed order. Do not label a request as booked. |
| Restaurant table booking (future capability only) | Branch, date, party size, meal period and real seat availability | Never show a booking CTA before table inventory and confirmation rules exist. Offer useful sold-out alternatives. |
| Branch banquet/event venue | Venue and verified photos, occasion, date/time, guests, package and optional food | Separate estimate from approved price, hold from confirmed booking, and deposit from remaining balance. Show terms and the next milestone. |

## Interaction and content rules

- Show branch, IST date/time, guest count or item quantity, and next action at the point of decision. Use one clear primary action. Avoid repeated entry of unchanged details.
- Keep the loading screen and skeletons in the same palette and typography, with reduced motion and immediate access to recovery. Cover missing media, empty inventory, sold out, changed capacity, expired hold/quote, pending payment, confirmation, cancellation and interrupted-session recovery. Never fabricate ratings, venue prices, availability, interiors or booking status.
- Check keyboard operation, reduced-motion poster, readable contrast, 400px phone and large text. On mobile, surface the current step and order/booking summary without hiding branch context.
- Business-controlled images, video, descriptions and policies should flow through authorized admin publishing with preview and fallback. A control must not appear before the public capability it manages exists.
- QA includes connected desktop/mobile prototypes, owner review, real DEV content, flag ON/OFF, and a happy path plus sold-out/conflict/recovery path. Do not infer live functionality from concept art.

Related work: SCRUM-24, SCRUM-26, SCRUM-33, SCRUM-35, SCRUM-42, SCRUM-44, SCRUM-45, SCRUM-95 and SCRUM-105. Update this contract alongside any deliberate customer visual change.
