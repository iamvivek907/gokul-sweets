# Customer experience audit implementation

All P1/P2/P3 items belong to this one pull request against `dev`. Existing prices,
stock, pickup commitment, identity, payment recovery, launch animation and the
owner-requested fixed pinch-zoom setting remain authoritative.

| Priority | Audit item | Implementation and validation |
| --- | --- | --- |
| P1 | Reliable opening | Keep cached home settings during refresh failures; bounded eight-second refresh; preconnect to API origin; sampled settings timings. |
| P1 | Food above the fold | Put authored phone campaigns after four complete dish cards; keep size families intact; compact branch introduction. Narrow-screen browser geometry assertion. |
| P1 | Calmer checkout | Compact verified identity presentation, preserve verification and pickup guards; omit settled empty add-ons; readable optional cards. Existing checkout suites remain required. |
| P2 | Useful dish information | Owner-authored serving, ingredients, allergens, spice level and content checklist; bounded fields and branch authorization. No fabricated food facts. |
| P2 | Consistent food imagery | Branch/SKU focal point with phone/desktop admin previews; apply to dish cards/details/collections; responsive image delivery. |
| P2 | Shared visual system | Final customer palette and control/metadata tokens in customer-design; scoped discovery controls, readable metadata, focus/reduced-motion inherited from shared shell. |
| P2 | Search | Local typo/transposition tolerance and Hindi aliases, keyboard suggestions and bounded branch-scoped recent searches; no per-keystroke API. |
| P2 | Returning customers | Device-only branch favourites and completed-order home shortcut; reuse guarded ReorderDialog. No automatic cart replacement. |
| P2 | Reviews | Dish/size and actual dish-star filters; published completed-order comments only; consent-safe first name; decreasing cursor, 20 comments/page, two bounded queries; concurrent index migration. |
| P3 | Genuine collections | Existing branch ranking and latest dishes plus owner-selected chef picks; current pickup eligibility filters; no invented bestseller claims. |
| P3 | Performance and maintainability | Responsive images, coarse sampled route/metric histograms, permission-protected on-demand instance report; fixed memory cardinality and no raw personal data. |

## Acceptance coverage

- Pure tests: preference bounds, storage failures, branch separation, group atomicity,
  search/Hindi/spelling, malformed optional facts, complete group campaign placement.
- Browser tests: 320/390px and desktop customer/admin journeys, review errors/retry,
  current-page rendering, saved-filter reload, keyboard search, crop preview,
  stale/denied owner edits, empty/error performance report, safe reorder cancellation,
  reduced motion, legacy flag-OFF isolation.
- Backend regressions: branch authorization, stale edits, hidden products, bounded
  public reads; review star semantics, moderation/completion/privacy and cursor bounds;
  aggregate histogram validation and staff authorization.
- Full CI includes production frontend build, lint, timezone tests, all existing
  browser suites, backend tests and deployment smoke checks.

## Honest content and measurement

The owner must enter confirmed dish facts and choose genuine chef picks; this change
never fills missing facts automatically. Performance percentiles are bucket upper
bounds from sampled client documents on the current backend instance, reset at restart,
not an all-instance analytics history. Physical-device and live payment-provider
acceptance are distinct from the browser fixtures and must not be claimed by CI.
