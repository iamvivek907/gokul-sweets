# SCRUM-37: account hub and purchase ticks

## Flag and ownership

`gokul.features.customer-account-hub=${GOKUL_FEATURES_CUSTOMER_ACCOUNT_HUB:false}` is OFF by default. Owner: customer identity and storefront. Enable only with verified customer OTP identity, the V77 migration, and DEV owner/reorder tests. Flag OFF retains the original Profile page. `/api/storefront/features` exposes the effective flag; the account API returns 404 when it is OFF. Existing cart availability, signed quote and inventory checks remain in force.

Saved preferences, favourites and addresses are keyed by environment and the exact currently verified subject, never looked up by an entered phone number. The account API checks the secure cookie, allowed Origin, trusted mutation and session on every call. Existing order history binds at checkout via verified ownership. These saved choices are not silently transferred across a new subject rotation; address and preference continuity after same-phone reverification needs a separate reviewed ownership migration. The UI explains that saved addresses are not automatic delivery eligibility.

## Purchase ticks

The backend computes an uncapped count from owned orders with a PAID payment and a non-cancelled order status, excluding refund and refund-pending orders. The frontend shows a tick at 1, 5 and 20 purchases: First visit, Regular, Gokul favourite. A tick is recognition only: no redeemable points, benefit or monetary claim. Guest purchases and phone-matched history do not qualify. If an order is cancelled or refunded, the current count can decrease and the displayed tick is recalculated.

## Reorder

Reorder begins with an owner-checked order endpoint. It requires the current selected branch to match the order's branch, and never clears an existing cart. It compares the order against the current menu, then checks combined cart item inventory and pickup slot capacity for the current date. It shows changed prices and unavailable lines and asks for explicit confirmation. Before writing the cart it repeats the preview, refuses any change and verifies the cart snapshot is unchanged. The ordinary checkout still obtains a fresh signed quote and commits only after its own inventory checks. This preview is not a reservation. Users choose another date or branch through the standard menu/cart journey.

## QA and deployment

Run frontend lint, TypeScript, customer theme and milestone tests; backend unit/integration tests and Flyway migration in CI. In DEV test guest, signed-in, logout, cross-account access, deleted address, stale favourite, changed price, out-of-stock item, cart changed during preview, and IST date boundary. Validate actual payment, browser and staff flows manually before enabling. Keep SCRUM-37 open until merged dev is deployed to DEV and both flag states are smoke tested.
