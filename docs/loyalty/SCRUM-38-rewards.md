# SCRUM-38: Gokul rewards and checkout savings

Base: dev `6f241aba4d111c9d99016364b850e50dad1ab9ec`.

## Customer contract

₹10 of eligible product spending earns one coin, rounded down per order. GST, priority/convenience/delivery/payment charges, reward savings and allocated coupon savings do not earn coins. Credit follows provider-confirmed payment and PICKED_UP / DELIVERED completion, with the configured qualifying minimum applied to net eligible products. Paid pending estimates use that same minimum, test/promotion exclusions and proportional coupon allocation; unpaid orders show no pending coins. Newly enrolled, exact verified subject ownership is required; old phone-matched orders never create a balance. Gift-card SKUs and excluded promotions must be configured as exclusions before sale.

The initial catalogue is 30 coins → ₹5 (minimum ₹149), 75 → ₹15 (₹249), 150 → ₹35 (₹399), 300 → ₹75 (₹699). The independent 10% eligible-product cap makes the last tier usable from ₹750. A tier is never partially priced or charged as if coins were rupees. Only its coin cost is reserved; the rest of the wallet remains available. One reward and one coupon may be selected. Rewards apply first. Coupons qualify against the remaining product subtotal, without fees or GST. Explicit customer coupon choices survive automatic best-offer calls and reward changes when still eligible, including weaker choices. An ineligible saved coupon falls back to the current best offer. Clearing a coupon also clears its manual-selection marker.

Mobile keeps consolidated checkout. Change offer opens a native, keyboard-dismissible dialog with a visible Close action, eligible offers and an optional code field. A successful server check applies the choice and closes the dialog. Failed checks preserve the previous quote and choice. Desktop retains its existing checkout steps and gains the same savings controls in Offers. Loading, retry and accepted-total checks remain visible before gateway handoff.

## Ledger and lifecycle

The immutable ledger is scoped by environment and exact verified subject. Accounts serialize spending using PostgreSQL row locks. Immutable unique event keys make completion, reservation, consumption, restoration, refund and expiry retry-safe. Mutable lots and holds are projections, not journal replacements. Credits consumed by a refund create debt rather than negative available coins; future credits offset that debt first. Expired credits are not reversed twice. Restorations retain their original earning source and expiry.

Coin expiry is 180 days after the last qualifying completed order. Activity extends unexpired lots; already expired coins never revive. Scheduled and on-demand expiry append explicit debits. Payment creation rechecks the reserved reward, current catalogue, product exclusions, cap and coin expiry. Provider failure that preserves the order also preserves its reward hold. Cancellation and confirmed refunds restore held/spent rewards once. Provider refunds and fulfillment completion reconcile inside their existing backend transactions. A bounded background worker repairs interrupted callbacks and performs expiry, including while new enrollment is OFF. Failed orders/accounts are logged individually while the remainder of the batch continues. Order/kind lookups have a dedicated ledger index.

Current provider refund flows are whole-order refunds. A future partial-refund UI must supply backend-verified product amounts and extend proportional earning/reward reversal; it must not call administrative adjustment from a customer callback.

## Rollout and funding

Rewards default ON following the owner's activation request. `GOKUL_FEATURES_GOKUL_REWARDS=false` remains an explicit environment override. Effective dependencies: verified customer OTP identity and accepted checkout quotes, together with their existing environment/session safeguards. OFF hides customer rewards and blocks new reward reservations; existing terminal holds/refunds still reconcile. Owner controls are at `/admin/loyalty` and remain available for audit while checkout rollout is OFF. Changing the repository default does not change an existing explicit environment override or deploy the feature.

Configurable server properties: earning divisor, maximum redemption percentage (up to 10%), expiry (up to 180 days), normal cost ceiling (up to 3%), qualifying minimum and welcome coins. The 15-coin welcome bonus is configurable but defaults to 0. At ₹149, the normal 14 coins plus 15 bonus coins have a ₹7.25 maximum face liability (4.87%), before any streak benefit. Finance must fund that additional promotion before enabling it. Never describe that combination as a 3% normal-cost program.

Owner-only catalogue and exclusion changes require an audit reason. Balance adjustments require the exact verified environment/subject, a UUID idempotency key and a reason; the ledger records old/new balances and staff identity. Administrative debits cannot exceed available coins. Dashboard liability includes paused reward tiers and separates active reward reservation amounts. Ledger access never falls back to searching by customer phone.

Campaign multipliers and physical Sweet Streak fulfillment are separate funded rollout work; this change does not advertise or grant unfunded free products, fabricate stamp benefits or claim that a campaign budget has been approved. The owner's activation request enables the implemented checkout/ledger program only; welcome coins remain 0 by default and separate campaigns remain outside this rollout. DEV provider QA remains a release validation step.

## Economics and QA

`python scripts/loyalty-economics.py` calculates the ladder, 25/50/80/100% redemption liability and 1,000/10,000/50,000/100,000 customer simulations at ₹150 AOV and monthly frequencies 1/1.5/2/2.5/3. Values are scenarios, not measured uplift. Without actual product cost, order contribution, marketing costs and treatment/control data, incremental profit and retention claims cannot be inferred.

Regression coverage includes fee-preserving reward-first coupons, cap/minimum rejection, duplicate completion events, immutable journal enforcement, owner/environment isolation, expiry, refund reversals and 100 concurrent spends/completion callbacks. Browser scenarios cover weaker explicit coupons, successful redemption, failed offer/reward checks, OFF and desktop. Existing mobile order/payment/cart flows remain in CI. No merge, deployment or Jira Done transition is authorized by this implementation request.

Accepted mobile quotes and desktop reward selections bind the current catalogue and earning-policy revision. A changed coin cost requires a fresh review even if the rupee savings are unchanged. Enrolled orders retain their earning divisor, qualifying minimum, welcome allocation and expiry period; pausing the rollout blocks new rewards but preserves promised completion credits and refund reconciliation.
