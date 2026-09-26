# Customer theme contract

The approved Sprint 2 customer visual direction lives in the shared customer shell. New customer stories inherit it automatically when they render inside `AppShell`; staff and admin screens keep their own design.

## Implementation rules

- Keep customer pages inside `frontend/components/layout/AppShell.tsx`. The shell owns the header, branch control, footer, mobile drawer and bottom navigation. Do not add a second customer header or mobile navigation in a page.
- Add visual rules to `frontend/components/layout/futuristic-storefront.css` under `.future-storefront`, or to a feature stylesheet scoped beneath that class. Reuse the existing ink, muted, gold and border variables and the shared focus and reduced-motion treatments. Keep flag-OFF presentation intact.
- Checkout's frame and navigation are in `frontend/components/checkout/checkout-experience.css`; keep it visually aligned with the shell. Preserve checkout's functional branch change and progress flow.
- `futuristicStorefrontV2` is the independent customer-wide visual switch; the effective `checkoutExperienceV2` also turns on the shared shell so checkout never mixes old navigation with the new frame. The backend publishes effective values via `/api/storefront/features`. Do not use a client-only flag or hardcode an always-on theme.
- Check desktop and narrow mobile for home, menu, product, cart, checkout, orders, profile and the drawer. Check keyboard focus, reduced motion, long branch names, and a cart with items before approving a new customer story.
- A deliberate rebrand should change this contract and shared components in one reviewed PR. Do not restyle a single story into a separate visual system.

The `frontend/tests/customerThemeContract.test.cjs` regression runs in GitHub Actions. It checks that the shell still owns both navigations, loads the scoped stylesheet, and uses the server's effective feature flags. Visual review is still necessary for new pages; a static check cannot prove every layout is consistent.

## Approved branch menu and pickup presentation

When the backend enables the customer visual flag and `contextualStorefrontV2`, `/menu` uses the editorial branch layout. Its gallery shows only real live-menu product photos, labelled by product name; it never pretends to show a branch interior. A branded fallback replaces missing imagery. Prices, units, availability, cart actions and branch switching retain their existing authoritative sources. The standard menu remains the flag-OFF path.

When effective `checkoutExperienceV2` enables the enhanced pickup step, the same editorial shell presents available slot choices as buttons instead of a long dropdown. The existing cart-wide availability response determines enabled times, priority charges and conflict recovery; the standard selector remains the OFF path. The global loading surface shares the approved teal palette and respects reduced-motion settings. Future restaurant-table, bulk occasion and banquet booking work follows `branch-booking-visual-contract.md` after that contract is merged.
