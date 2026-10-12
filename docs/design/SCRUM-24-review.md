# SCRUM-24 · Gokul Sweets connected design review

Open [the standalone prototype](./scrum-24-connected-prototype.html) in a browser. Screen tabs and action buttons connect nine concept screens. The toolbar switches to a 390 px phone, removes media, and enlarges type. This document and prototype contain no running checkout, account, or payment code.

## Owner decisions required

| Decision | Proposed direction | Confirm or revise |
| --- | --- | --- |
| Palette | Warm cream `#fff9ef`, wine `#77182d`, gold `#ae7725`, near-black `#332225`; color always paired with readable text | Owner sign-off pending |
| Type | Georgia display headlines, system sans-serif UI; no remote font dependency | Owner sign-off pending |
| Voice | Warm, clear and specific: pickup promise before payment; a status-check outage never called a failed payment | Owner sign-off pending |
| Media | Actual branch/storefront hero and product/occasion photographs, with rights and approved crop/alt text; CSS illustration marks unfilled slots | Real approved photography pending |
| Journey | Welcome → home → menu/pickup → conflict resolution in place → payment → result → tracking; occasion enquiry remains separate | Owner walkthrough pending |

## Flow and truths

- First visitor can choose a branch or browse without location permission. Returning visitor sees their last valid branch but can change it where they are. Browser storage is a preference, not authoritative availability.
- Home hero uses published media when present and a text-led fallback otherwise. A failed image must not leave blank cards. Menu prices represent shop prices; the backend quote remains authoritative.
- Pickup time is proposed, then committed by the server along with latest inventory and quote. An expired/invalid slot presents alternatives within checkout and preserves the cart.
- Payment polling begins after gateway opening/return, not merely when payment page mounts. Unknown/503 is not FAILED. A failed order releases its hold; retry requires a fresh validated slot and must not create a blind second charge. Leaving keeps the cart; discard must be explicit.
- Tracking uses committed IST pickup windows and truthful backend states; active pickups sort by upcoming pickup time, while completed/failed items move to history. SCRUM-102 implements that behavior, so its preview is aspirational.
- Occasion and banquet screens are concept previews; inquiry, eligibility, pricing and deposit logic belong to future stories.

## Review paths

1. First-time visitor: Welcome, choose branch, Home, Pickup, Payment, Tracking.
2. Returning visitor: Home, change branch, Conflict, recover a pickup time without losing items.
3. Payment uncertainty: Payment, Payment result, manual order record; verify no promise of success after provider error.
4. Small phone, large text, keyboard only, reduced motion, no photo. All navigation and buttons must remain usable; the final implemented screens require browser accessibility tests.

## Delivery boundary

This story approves a direction, not final production design or functionality. No runtime toggle, migration, or environment variable is introduced. Implementation belongs to SCRUM-25 through SCRUM-28 and SCRUM-95, with their separate OFF-by-default toggles and QA gates. The owner must approve visual direction and real media before SCRUM-24 can be Done.
