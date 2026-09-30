# SCRUM-110: notification organisation and occasion booking

One consolidated defect and one PR. Related QA stories: SCRUM-35, SCRUM-39, SCRUM-109 and gifting SCRUM-50.

## Customer walkthrough

Select a branch, then **Occasions & gifting** from its Home/Menu/Branch details navigation or the shared drawer. Browse before sign-in; a compact verified contact confirms the account used to submit.

Choose a future date and select bulk sweets or a published gift box. Weight sweets support kg or pieces. A 1,000-piece Kaju Barfi enquiry remains a request for 1,000 pieces. The manager either uses a measured configured piece size or explicitly approves the total grams for production. No guessed conversion or payment is permitted.

For 700 boxes with 4 Barfi, 2 Peda and 2 Laddoo each, the form requests 2,800 / 1,400 / 1,400 pieces. The server checks those totals, packaging publication, piece capacity and lead time. The manager confirms actual physical fit and any branding.

A reviewed quote contains inclusive item totals, applicable configured item taxes, the packaging total included in those prices, deposit/balance deadlines and a dedicated production plan. Packaging is bundled into the inclusive item prices; it is not an additional checkout charge. The customer sees requested quantities, approved production quantities and packaging details before paying. Existing deposit, balance, readiness and cancellation flows apply. A quote creates no paid booking.

## Admin setup and review

**Occasion food enquiries → select branch → Occasion sweets & packaging catalogue** requires `MENU_MANAGE`. Create products and actual product photos through the existing menu editor; configure occasion-only/publication/lead days/measured piece grams here. Occasion publication is independent of the daily menu availability switch; an active sweet can be offered for future production even when daily pickup is unavailable. Occasion-only items are excluded by the regular menu and checkout repository queries. Fields include visible helptext.

Add or edit packaging with up to six uploaded actual photos, usable dimensions, material, compartments, conservative piece capacity, estimate, branding and sourcing lead days. Empty photo is a labelled placeholder. An empty price is an enquiry requiring reviewed pricing. Unpublish stops new selection and preserves existing enquiry snapshots.

`APPROVAL_MANAGE` users review configured unit rates and estimated kg or measured piece size. The server calculates product amounts, configured tax, box count × inclusive per-box rate and accessory quantity × inclusive rate. It adds packing costs once and allocates them across the existing item tax snapshots. The packaging-fit checkbox is required for gift quotes. No manual inclusive line-total calculation is needed. Quote changes are restricted to open requests; held/paid bookings retain their approved snapshots.

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

## Post-deployment occasion experience follow-up

- Branch tabs: Home, Menu, Occasions & gifting, Branch details.
- Photo-led categories use actual product categories and the published branch catalogue. Sweets, paneer and fast-food categories can all be requested. Quantities persist across categories; the basket shows kg/pieces and per-box totals with removal controls.
- Quantity/unit controls align at the bottom of product cards. Quote tracking has a dedicated `/occasions/requests` page linked from occasion planning, branch pages and notifications. It shows bounded expandable cards, search, cursor-based earlier requests, milestones and secure payment/order actions. New requests navigate directly to their card.
- Admin branch selection uses the shared per-staff preference, validates current access and persists across reload. Branch switches reset the catalogue editor.
- Packaging supports up to six real JPG/PNG/WebP photos, 5 MB each; preview, remove, save and publish. First photo is the cover; customers can inspect every gallery view. Existing snapshots retain their gallery; legacy single-photo packaging remains usable.
- Branch branding includes headline, description, real uploaded campaign photo and publication. Unpublished content uses the standard introduction. Fields include helptext. Uploads enforce branch access and MENU_MANAGE, using existing R2 static-content signature/size validation and immutable URLs.
- Review action has explicit cream text on deep teal. Browser checks computed colours, category retention, 700-box arithmetic, dedicated tracking navigation, gallery browsing, upload/removal, campaign publication and branch refresh retention.

After merge deploy backend and frontend (Flyway V95–V97), and test real R2 uploads, published/unpublished campaigns, gallery persistence, quote review/payment and mobile navigation. No new secrets beyond existing R2 configuration.


## Automated quotes and final measured packing

1. Publish a real 400 ml plastic box (capacity at least three pieces, one compartment may contain the mixed assortment), set its inclusive estimated rate and upload its photos. Choose it on the customer occasion page; enter 600 boxes with one Kaju katli, one Mathari and one Chena roll per box. The request is 600 pieces of each. Select the optional one-spoon-per-box choice when needed.
2. A manager chooses **Final weight measured after packing**, reviews the branch's configured pre-tax rate per kg and estimates total kg per sweet. Fixed quotes can use configured grams per piece; no weight is guessed. Input the box rate and a spoon rate for 600 spoons. Calculation shows food, food tax, packing/accessories, estimate, advance and balance automatically.
3. The customer's date cannot be edited. The manager chooses readiness time on that date and accepts packaging-fit review. Rates/amounts must be calculated and reviewed again if inputs or authoritative totals change before sending.
4. The customer opens **Requests & quotes**, sees estimated kg × agreed rates, separate box/accessory costs, advance and balance. An estimated quote requires acceptance of actual-weight pricing before advance checkout; this acceptance is timestamped/audited. Slots before the approved ready time are omitted and independently rejected by the server.
5. Verified advance commits dedicated bulk production. Daily pickup inventory is unchanged. An estimate cannot use full advance or collect its balance before final weighing. Production, piece counts and date remain visible while packing is pending.
6. After physical packing on the requested India date, the manager records actual total food kg excluding the container, confirms all requested pieces/boxes/accessories and reviews the computed final invoice. Only one finalization is allowed; stale revisions and existing balance attempts block edits. The server keeps the agreed rates/tax and fixed accessory charges, adjusts production/readiness automatically and creates an immutable audit snapshot.
7. The customer receives an invoice-ready notification linked to this request, reviews the final total and pays only the remaining amount. Paid advance remains credited. If the actual final invoice is below money collected, the credit is recorded for finance review; no refund is falsely reported. Verified payment creates the operational order using final measured quantities and preserved tax snapshots. Completion/read/review notification behaviour still applies.

Example arithmetic for one weight-priced sweet: estimated 600 pieces → 12 kg at ₹400/kg, food ₹4,800 + configured 5% food tax ₹240; 600 boxes × ₹10 = ₹6,000; 600 spoons × ₹1 = ₹600. Estimated total ₹11,640, 25% advance ₹2,910. If packed food weighs 12.5 kg, the fixed food rate gives ₹5,000 + ₹250 food tax, unchanged packing/accessories ₹6,600, final total ₹11,850 and remaining balance ₹8,940. These are illustrative rates; live configured rates govern actual bookings. Packaging/accessory charges follow the established inclusive allocation into item tax snapshots; this PR does not introduce a separate packaging tax regime.

## Directory, reports and operational dashboard

- Only server-verified provider proof promotes the directory badge; a typed phone does not. V96 backfills already verified registry phones; later contact creation preserves verification. Completed occasion orders now attach the canonical contact, so directory counts and customer analytics include them.
- **Today at a glance** precedes quick-access links, uses assigned branch access and shows paid operational orders due today, preparing, ready for pickup and completed for the booked India service date. It refreshes every 30 seconds and shows loading/retry states instead of invented zeroes.
- Reporting analytics previously required manual rebuild. Automatic refresh is enabled by `gokul.reporting.automatic-refresh` (env `GOKUL_REPORTING_AUTOMATIC_REFRESH`, default true), with a one-minute delay. Initial/daily backfill maintains rolling windows; later changes rebuild only affected service-date/customer summaries. A shared transaction advisory lock and repeatable-read snapshots prevent overlapping or partial rebuilds. Reports refresh while visible; zeroes remain valid for a branch/date range with no completed collections.
- Calendar choices before tomorrow, after one year, or before selected food/packaging lead time are unavailable. Keyboard focus, Escape/close, future-date server checks and India date semantics remain required.

Deploy backend and frontend together; Flyway V95–V97. Existing occasion/bulk/identity/payment/inbox gates remain authoritative. No new credentials. Real packaging fit, actual weighing, payment-provider callbacks, live phone proof, delivered push, R2 photos and finance credit handling remain deployment QA.

## Multiple packing groups, rebates and daily kitchen workspace (V98)

- The celebration basket is a compact inline, expandable summary on desktop and phone; it never floats over products. Product cards show the authoritative branch rate including configured food tax. Known-price subtotal is clearly partial when piece sizing, spoon rates or packaging prices need review.
- Bulk quantities and each packing group are additive. Customer adds 1,000 mixed boxes with one piece each of three sweets, 100 kg bulk paneer and three Gulab Jamun weight groups: 10 kg in 1 kg packs (10 boxes), 5 kg in 500 g packs (10 boxes), 5 kg in 250 g packs (20 boxes). No separate 20 kg entry or manual box calculation is required. The same sweet may occur in mixed piece boxes and separate kg packs; requested pieces and additional grams stay distinct until measured production review.
- Weight packaging must be a published real 1 kg, 500 g or 250 g box configured by the branch. Container volume (e.g. 400 ml) does not imply food kg capacity. Validate whole packs, matching capacity, piece capacity, quantities and lead days independently on the server. Mixed pieces can share a compartment after physical-fit review. Optional spoons are counted across selected groups. Existing single-box requests retain their snapshots and checkout flow.
- Quote editor prices each packing group per box, calculates product tax and allows item food rebates and a bulk food rebate. Each percentage is 0–99%, up to two decimals; apply item rebate, then bulk rebate to the remaining food amount, before tax. Box/accessory charges are excluded and have separate agreed rates. Helptext and customer price explanation show this order. Agreed rebate percentages persist through actual packed-weight finalization. No under-supply of requested kg is allowed.
- Admin date strip follows the supplied planning reference: seven horizontally scrollable date tabs with counts, previous/next week and Today. Three-day outlook keeps dates separate. Selecting a date shows full server product totals above compact expandable request cards. Quotes, packing and cancellation remain inside the selected card. Card pagination is 50 per page; totals/counts include all matching records, including beyond 100 requests. Dates and visibility refresh use India time.
- Requested/unpaid quantities, paid dedicated commitments, kitchen-approved quantities and physically prepared quantities have distinct labels and units. Approving pending paid product quantities records an audited kitchen approval in the dedicated bulk production register. It does not increase daily pickup inventory or record physically ready stock. Quote approval and verified advance still create/commit the underlying dedicated allocation automatically. Stale approval snapshots return conflict; retries cannot duplicate production. Inventory production screen links to this register.
- Workspace refreshes every 30 seconds while visible and offers Refresh now. New request and verified advance create deduplicated staff inbox/push events using existing subscribed devices, session checks and branch/permission eligibility. Push opens the correct branch/request/date. Quote/decline acknowledges request-review alerts; approving all pending kitchen products acknowledges advance-planning alerts. Read alone does not complete unresolved tasks.

QA: reproduce the exact mixed/paneer/Gulab packing scenario, same-sweet pieces plus kg, invalid partial packs/box capacity and changed catalogue prices; review item and bulk rebates and final weight tax arithmetic; open date tabs on phone and desktop; verify no daily stock increase on approval, stale/repeated approval, 150-request totals versus paginated cards, auto-refresh, staff permissions, direct notification opening and actual DEV push delivery. Deploy backend and frontend together with Flyway V98; no new credentials.


### Monthly planning calendar and distinct enquiries

- Staff select **Open calendar** on the occasion production page. Every date shows its branch-scoped booking/request count, including zero. Month and year controls jump to future pickups (within the existing one-year booking horizon); historical dates remain accessible for two years.
- Select a date to load the existing daily production totals and full order cards, with all detail and operational actions unchanged. Older cards remain accessible with Load more. Counts refresh with the visible page, including manual refresh. Failed calendar loading has retry; branch changes cannot reuse another branch's counts.
- Customer can send more than three distinct enquiries in a day. Retry/double-click of an identical request within 15 minutes returns the existing request and does not create another staff alert. Verified identity, catalogue validation and lead-time checks still apply.
- QA: submit six different requests with one verified customer; retry each and check only six records. Calendar should show all six, regardless of card pagination. Jump to a later month, select a booked date, open its card, verify details/actions, then switch branch and verify counts. Check phone width, zero-order dates, failed load/retry and live count refresh.
