# Offer category and item exclusions

In **Admin → Storefront → Offers**, create or edit an offer and use **Excluded categories & items**. Exclusions belong to that offer, independently of Gokul rewards. The welcome offer is not created or enabled by this migration.

A category rule covers products currently in that category, including future additions. An item rule covers its exact product variant. Either rule excludes a product; an item selection does not override a category exclusion. Category moves affect future eligibility checks.

For an offer with exclusions, minimum spend, percentage/fixed discounts, and slab targets use eligible product subtotal before item tax and fees. Order-level loyalty savings are allocated proportionally across product spend first. The discount cannot exceed eligible spend. All excluded products, taxes and fees remain payable. Existing offers with no exclusions retain the existing pricing basis.

The same server calculation handles preview, public offer selection, explicit codes, and payment revalidation. Started payments and completed orders retain their stored amounts. Existing staff authorization and offer branch scope remain in force. Old clients omitting exclusion fields preserve saved rules; explicitly sending empty arrays clears them.

## Dev acceptance

1. Create a 10% offer, minimum ₹300 and cap ₹50; exclude **Beverages** and one individual snack variant. Save, reopen and verify both selections.
2. ₹300 eligible food + ₹100 drinks: ₹30 discount, with the full drink price, taxes and fees still payable. ₹250 eligible food + ₹100 drinks: no offer.
3. Drinks-only and the individually excluded item alone: no discount. Another size/variant is eligible unless separately excluded or its category is excluded.
4. Add a new beverage product: it is excluded without editing the offer. Overlapping category/item rules do not double-subtract.
5. Repeat with a fixed discount and spend-based slabs. “Add more” targets count eligible spend only; restricted offers do not label arbitrary add-ons as reaching the threshold.
6. Repeat with loyalty savings. Check that the discount is calculated after the proportional loyalty allocation and cannot exceed remaining eligible product spend.
7. Change exclusions after selecting an offer but before Pay: the existing server revalidation rejects an invalid or changed discount and requires review. Do not change a started payment.
8. Clear both exclusion lists and save. Reopen to confirm they are empty. Test an existing offer with no exclusions for unchanged pricing.

Automated coverage includes price/threshold/fee regressions, explicit-code revalidation, manager access guards, API round-trip validation, PostgreSQL migration/persistence/catalogue scope, and a browser form round-trip at 320/390/1280 px in CI.
