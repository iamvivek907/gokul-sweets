# Sprint 3 delivery pilot gate (SCRUM-31 and SCRUM-32)

These changes are OFF by default. Owner manually deploys merged `dev` to DEV after the sprint; a PR merge must not trigger Vercel deployment.

## Economics (SCRUM-31)

Owner: finance and operations. Dependency: SCRUM-30 exact address eligibility, capacity, signed quote and rider/stock reservation. Set `GOKUL_FEATURES_DELIVERY_ECONOMICS=true` only with reviewed `DELIVERY_ECONOMICS_VERSION`, `DELIVERY_ECONOMICS_COST_VALID_UNTIL` (inclusive IST date), `DELIVERY_ECONOMICS_FOOD_COST_RATE`, `DELIVERY_ECONOMICS_PACKAGING_COST`, `DELIVERY_ECONOMICS_LABOUR_COST`, `DELIVERY_ECONOMICS_WASTE_COST`, `DELIVERY_ECONOMICS_PAYMENT_COST_RATE`, `DELIVERY_ECONOMICS_JOURNEY_COST`, `DELIVERY_ECONOMICS_REMEDY_COST`, `DELIVERY_ECONOMICS_FEE_FLOOR`, `DELIVERY_ECONOMICS_FEE_CAP`, and `DELIVERY_ECONOMICS_MINIMUM_CONTRIBUTION`. Rates are fractions, e.g. 0.40, money INR. Review cost version daily or extend date only after finance validates the recipe and journey assumptions. Changing inputs invalidates outstanding five-minute signed quotes; customer reviews the updated amount.

Food price stays identical to pickup. Tax is excluded from contribution. Fee meets the configured floor unless the needed fee exceeds the customer cap; that trip is stopped before payment and pickup is offered. Snapshot migration V73 preserves quoted inputs, fee and contribution. `REPORT_VIEW` plus branch access permits reading `/api/admin/orders/{orderId}/delivery-economics`; refunds adjust the reported margin. Safe fallback: turn the economics flag OFF, retaining existing delivery quote behavior with zero delivery fee. Existing orders and snapshots are retained.

## Dispatch pilot (SCRUM-32)

Owner: branch operations. Dependencies: SCRUM-31 economics approval, SCRUM-30 delivery lifecycle. Set `GOKUL_FEATURES_DELIVERY_DISPATCH_PILOT=true` only after those flags and the signed delivery checkout are enabled, pilot rider roster and per-window availability are configured, and branch service windows match measured kitchen readiness. Migration V74 stores rider availability, one active assignment per rider/window, exception contact and actual journey outcomes.

`/admin/delivery-dispatch` allows branch staff to see two days of active orders, register riders and availability, assign riders, contact customers and record exceptions/costs according to permissions. The existing order workflow refuses READY_FOR_DELIVERY -> OUT_FOR_DELIVERY without an assignment while the pilot flag is ON. Rider/window uniqueness and a locked window capacity check avoid simultaneous over-assignment. Grouped trips are intentionally unavailable until measured feasibility rules are implemented. Pilot quote windows require a configured available rider in addition to existing reserved-count capacity. Safe fallback: turn pilot flag OFF; established SCRUM-30 delivery lifecycle resumes, records remain for audit.

## DEV manual checks still required

Confirm flag OFF and ON, tiny baskets, long trips and bad-cost expiry; payment amount and refunded margin; competing assignments, unavailable rider and capacity; rider no-show and re-assignment, bad roads, missing address, early food, no signal and failed handoff; customer contact and actual costs; IST across server time zones. A manual deployment and owner review are required before Jira QA.
