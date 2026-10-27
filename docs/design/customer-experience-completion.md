# Customer experience completion

This change is based on the customer audit after PR 240 and ships as one PR against dev. Pinch zoom remains disabled as requested.

| Priority | Finding | Implementation |
| --- | --- | --- |
| P1 | No standard pickup capacity across the checked dates | Explain that online availability does not mean the physical shop is closed, retain change-time/call actions, and add an on-demand eight-date admin readiness check with scheduling, inventory and hours links. No automatic production configuration changes. |
| P1 | Menu takes too long to reach ordering | Compact branch identity, remove the redundant menu photo gallery, place search/pickup before promotions, move discovery below products. Branch-home photography remains. |
| P1 | Ambiguous unavailable items | Preserve separate sold-out, service-hour, checking, failed lookup and dated-pickup decisions. Existing authoritative guards still control additions. |
| P2 | Weak product information | Product-details dialog, branded broken/missing-photo fallback, suppress generated placeholder descriptions, admin guidance to enter factual taste/serving information. Size-specific descriptions remain available in the variant picker. |
| P2 | Desktop size duplication | Reuse actual SKU grouping and the guarded size picker on desktop; retain the desktop cart flow. |
| P2 | Price consistency | Compact INR prices retain paise, validated comparison amounts and highlighted selling prices; truthful weight and minimum-quantity labels. |
| P2 | Repetitive empty ratings | Hide unrated product rows and empty aggregate/top-item sections; retain a single paginated public-comment surface. Names and consent semantics stay unchanged. |
| P2 | Long occasion form | Four anchor stages, persistent indicative/partial estimate, review shortcut, bounded catalogue request and explicit retry. Native form validation and server quote acceptance are unchanged. |
| P2 | Review Hindi gaps | Translate review controls, star labels, feelings, suggestions, errors, counters, consent, saved view and return prompt; preserve customer-written text and product names. Translate public-review pagination and new planning labels. |
| P2 | Accessibility and shared visual consistency | Shared customer-only colour, card, bill-total, spacing and focus refinements; wrapping enlarged text; scalable review type; respect reduced motion in CSS and programmatic scrolling. Flag-OFF/staff styling remains isolated. |
| P3 | Ordering funnel visibility | 10% document sampling alongside existing vitals; allowlisted menu/add/checkout/payment-start and server-reported paid/failed/expired counters. Per-document deduplication, coarse page names, no IDs, comments or persistent tracking identifiers. These counts are not order-level conversion attribution. |
| P3 | Branch SEO | Bounded, cached public branch lookup; distinct titles, descriptions, canonical/Open Graph URLs, active-branch sitemap entries and escaped Restaurant JSON-LD using configured address/location/photo. No invented opening schedules, dietary claims or self-review stars. |
| P3 | Regression and visual validation | Node positive/negative/edge coverage; browser coverage for details/fallback/focus/larger text at 320/390/640/1280, shared desktop sizes, admin readiness empty/error/recovery, and occasion retry/stages. Existing payment, review pagination, flag-OFF, checkout and design suites remain required. |

## Release checks

Run lint, TypeScript, all Node tests in UTC and America/Los_Angeles, production build, backend tests/format gate and all existing browser CI shards. Browser screenshots are uploaded as CI artifacts. Real iOS/Android device, screen-reader and production-payment acceptance require a device/operator; browser automation does not establish those results.

The live pickup-capacity finding needs the branch operator to inspect configuration using the new readiness check. It cannot be corrected by changing presentation or safely guessing stock. Search placement also depends on indexing, business-profile consistency and competition; this PR cannot guarantee first place.
