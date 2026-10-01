# Customer theme contract

## SCRUM-107 owner-approved arrival palette

The arrival screen and approved eight-item menu demo define the shared customer appearance when the effective customer visual flag is on: deep teal `#092725`, hero teal `#143936`, cream `#fffaf2`, coral action `#c76752`, readable ink `#172e2c`, and light bordered surfaces. `frontend/components/layout/customer-journey.css` applies these tokens within `.future-storefront` across menu, cart, checkout, orders, navigation and overlays. The server's effective flags still control the visual shell; the flag-OFF appearance is unchanged. Branch cards open the selected live menu. A cross-branch switch explicitly reviews the current cart and, only after confirmation, clears it, resets the selected pickup slot and opens the new branch menu. Placed orders are unaffected. A real paid `CONFIRMED` order alone receives the prominent confirmation tick.

Across customer pages, decorative text and action labels resist accidental selection; inputs, prices and order numbers remain copyable. The viewport fits narrow screens without page-wide horizontal movement, while intended carousels retain contained scrolling. Buttons use touch manipulation to avoid accidental double-tap zoom. The owner's subsequent request on 27 Sep explicitly opts for a fixed device-width mobile viewport without pinch zoom; the viewport declares `maximumScale: 1` and `userScalable: false`, and the customer shell allows panning while disallowing pointer pinch gestures. Browser or operating-system accessibility settings may override those controls. Layouts must still reflow with larger system text and desktop browser zoom.

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

## Hindi and compact mobile checkout

The shared customer and staff headers expose a labelled English/Hindi control at all times; language preference is independent of checkout rollout flags. Hindi uses native Devanagari font families, normal letter spacing and generous line height while retaining the approved colours. On phones, review and offers use one primary action in the page flow, beside the total. Avoid floating price tiles and repeated price confirmations. Language uses a compact header trigger with a popover, without adding a header row. Optional add-ons form one horizontal scrolling row with contained overflow and visible actions. The arrival header keeps the clickable brand at left and language at the far right. The language popover renders above clipped header containers so both choices remain visible on phones.
