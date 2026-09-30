# SCRUM-110: notification organisation and occasion booking

One consolidated defect and one PR. Related QA stories: SCRUM-35, SCRUM-39, SCRUM-109 and gifting SCRUM-50.

## Customer walkthrough

Select a branch, then **Occasions & gifting** from its Home/Menu/Branch details navigation or the shared drawer. Browse before sign-in; a compact verified contact confirms the account used to submit.

Choose a future date and select bulk sweets or a published gift box. Weight sweets support kg or pieces. A 1,000-piece Kaju Barfi enquiry remains a request for 1,000 pieces. The manager either uses a measured configured piece size or explicitly approves the total grams for production. No guessed conversion or payment is permitted.

For 700 boxes with 4 Barfi, 2 Peda and 2 Laddoo each, the form requests 2,800 / 1,400 / 1,400 pieces. The server checks those totals, packaging publication, piece capacity, distinct-sweet compartments and lead time. The manager confirms actual physical fit and any branding.

A reviewed quote contains inclusive item totals, applicable configured item taxes, the packaging total included in those prices, deposit/balance deadlines and a dedicated production plan. Packaging is bundled into the inclusive item prices; it is not an additional checkout charge. The customer sees requested quantities, approved production quantities and packaging details before paying. Existing deposit, balance, readiness and cancellation flows apply. A quote creates no paid booking.

## Admin setup and review

**Occasion food enquiries → select branch → Occasion sweets & packaging catalogue** requires `MENU_MANAGE`. Create products and actual product photos through the existing menu editor; configure occasion-only/publication/lead days/measured piece grams here. Occasion publication is independent of the daily menu availability switch; an active sweet can be offered for future production even when daily pickup is unavailable. Occasion-only items are excluded by the regular menu and checkout repository queries. Fields include visible helptext.

Add or edit packaging with an actual HTTPS photo URL, usable dimensions, material, compartments, conservative piece capacity, estimate, branding and sourcing lead days. Empty photo is a labelled placeholder. An empty price is an enquiry requiring reviewed pricing. Unpublish stops new selection and preserves existing enquiry snapshots.

`APPROVAL_MANAGE` users review each item price and production amount. The packaging review checkbox and final packaging total are required for gift quotes. Include packaging cost in inclusive item totals and explain allocation/custom branding in quote terms. Quote changes are restricted to open requests; held/paid bookings retain their approved snapshots.

Dedicated production is created on approval when the existing bulk-production toggle is enabled. Do not disable that toggle for bulk QA. No daily allocation is automatically inflated by these approvals.

## Notifications

Customer and staff inboxes group loaded updates by order/request and collapse older events into a timeline. Search and unread filters run on the server before bounded cursor pagination. Settings are collapsed separately. Staff orders with overdue actions sort first among the loaded results.

A committed stage transition acknowledges relevant preparation or readiness alerts for currently eligible branch staff. Read alone does not complete a task or suppress follow-up email. Failed/rolled-back transitions do not persist acknowledgement. Current lifecycle checks still control outbound reminders.

## QA matrix

- Mobile and desktop, guest browsing, verified account badge, account management and session expiry.
- 100+ updates, unread search beyond first page, cursor overlap, timeline expansion, older unread counts, offline refresh/read and source-action rollback.
- Piece request without sizing blocks quote; measured sizing or explicit approved grams creates the correct weight snapshot/production plan; requested pieces remain unchanged.
- 700-box recipe totals; mismatched totals, capacity, compartment count, unavailable packaging and short lead time are rejected.
- Real images and deliberate missing-photo fallback. Occasion-special filter and catalogue search preserve selections.
- Ordinary menu/checkout cannot buy occasion-only products, including forged product IDs.
- Catalogue/box edits and unpublication do not alter existing quote snapshots.
- Packaging approval and final cost are mandatory before quote. Deposit/balance arithmetic includes the packaging once.
- Multi-staff branch permissions; read-only actions do not stop overdue escalation. Successful preparation and ready actions acknowledge only the completed task family.
- Existing deposit replay, balance settlement, dedicated-production readiness and cancellation regressions.

Automated browser tests use synthetic API responses and never contact payment, email, SMS or push providers. Actual DEV delivery and real box photography/physical fit still require branch QA.

## Notification lifecycle follow-up

- Customer opening an order conversation or its actual order page acknowledges the related order and linked occasion updates. Failed/offline mutations do not fabricate successful read state.
- Customer and staff “Mark all read” uses a server watermark so notifications arriving after the displayed snapshot remain unread. All mutations preserve authenticated subject/staff, branch and environment isolation.
- Pickup/delivery automatically acknowledges preceding order/linked occasion updates transactionally. Replaying completion cannot clear subsequent refund alerts.
- One completion push includes a completely optional, honest review invitation and links to the existing review section. It respects subscription, quiet hours, freshness and retries; explicit acknowledgement or an existing review cancels an unsent invitation. No repeated review campaign.
- Badge counts represent unread order conversations. Device stages replace the previous notification for that order without renotifying. Routine customer updates leave the unread queue after seven days in bounded batches; history is retained and financial exceptions remain visible.
- Staff action from live orders continues to acknowledge only tasks resolved by the successful committed transition. Reading/marking all read does not complete a task or suppress overdue escalation.

QA: verify click-to-read, direct order opening, snapshot-safe mark-all, pickup cleanup and later refund preservation; linked occasion cleanup; opt-in completion push/review anchor; same-order device replacement; seven-day expiry and financial exceptions; cross-account/environment/branch denial and failed action rollback.
