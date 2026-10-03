# Menu service hours, branch operations and staff sessions

## Administration

Admin → Menu → Service hours & sold out provides branch-specific daily opening/closing times, weekdays, persistent sold-out switches and one ingredient dependency per item. MENU_MANAGE and the existing branch-access checks apply. Category actions copy hours to the current items only; future products need their own rules. Saving is atomic and revision checked; conflicting edits require a reload.

Service enforcement defaults OFF. Enable it for each configured branch. Opening is inclusive, closing exclusive, and every calculation uses server IST. Overnight windows belong to their opening weekday. Empty times mean all day on the selected weekdays. Sold out remains set until staff clears it. Dependencies propagate availability; they do not introduce ingredient stock consumption or replace existing inventory checks.

Example configurations: breakfast parathas 09:00–11:00, fast food/dinner 11:00–21:30, samosa 10:00–17:00, and chola samosa requiring samosa. Sweets can start at 08:00 with a business-selected closing time. These examples are not automatically enabled or seeded.

Rules govern when a new order may be placed, independently of its future pickup time. Direct cart, quote and order requests revalidate on the server. Acceptance holds a shared branch lock against rule changes and closure; read-only previews do not request write locks. An unchanged owned pickup reservation can continue payment recovery after closure or the end of service. Changes to its items or pickup require current availability. Existing product, inventory, slot and payment checks remain.

Admin → Branches & settings → Customer operations has an Operational switch, protected by BRANCH_MANAGE and branch access. It defaults ON and is separate from Active. Turning it OFF leaves the branch in discovery with “Currently not operational”, disables branch selection and blocks its public home/menu, bulk enquiry and new retail orders. Existing orders, staff access and payment recovery are retained. Customers with an already open page refresh branch status while visible; server validation takes effect immediately.

## Customer presentation

Unavailable scheduled items remain visible with a readable availability reason and disabled addition/increase controls. Server timestamps and a receipt-time monotonic clock schedule refreshes at service boundaries, including a prefetched menu opened later. Visible menus also refresh periodically and on returning online. Expired decisions are blocked during a failed refresh, retaining the saved cart. Mobile and desktop retain the existing shared menu and checkout presentation; disabled service enforcement retains the original live-menu filtering.

## Notifications and sessions

The kitchen sound preference is saved per staff account/browser/API environment. Refresh restores the enabled choice and polling; browsers require a user gesture before sound can resume. “Resume kitchen sound” and an explanation communicate that restriction, and “Disable kitchen alarm” clears the saved choice. Existing persistent Web Push registration, permissions, branch filtering and duplicate-alarm protection are retained.

Non-admin secure staff sessions have a 365-day idle lifetime renewed by valid authenticated requests. Renewal retains the token and therefore its push subscription binding. Roles whose names contain ADMIN receive an absolute 15-hour session with no sliding renewal. Existing shorter admin cookies keep their original expiry until the next login. Logout, account disabling, user changes, expiry, CSRF, MFA and the existing ten-minute sensitive-action reauthentication still apply. Renewal never revives expired or revoked sessions.

## Regression coverage

ServiceWindowTest covers IST boundaries, overnight opening-day ownership and selected weekdays. MenuServiceWindowsIntegrationTest covers menu/cart enforcement, dependency recovery, malformed rules, conflicts, read-only preview and closure locking. StaffSessionLifetimeIntegrationTest covers rolling sessions, fixed admin expiry and revocation. The service-hours browser suite exercises mobile/desktop configuration, disabled menu controls, an incorrect device clock, branch closure and stale-menu recovery. The existing checkout/kitchen suite also verifies alarm preference restoration and disabling after refresh. Real-device notification delivery and browser audio behaviour should be checked in QA.
