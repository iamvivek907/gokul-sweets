# Create menu categories before adding products

Admin → Menu → Menu categories (`/admin/menu/categories`) allows staff with
`MENU_MANAGE` to create categories independently of products and imports.
Categories are shared across branches; this page explicitly states that scope.
No schema migration, seeded production data or new rollout flag is required.

Enter the customer-facing name, a permanent unique code, optional description and
nonnegative display order. Codes accept ASCII letters/numbers, hyphens and underscores
and are stored uppercase. New categories are active. Empty categories do not create
stock or products. No category edit, deletion or deactivation is introduced.

`GET /api/admin/categories?page=0` returns `{content,totalPages,number}`, with 50
entries per page, ordered by display order and ID. It includes inactive entries so
operators can identify existing codes. `POST /api/admin/categories` accepts
`{code,name,description,displayOrder}` and returns HTTP 201 with the category summary.
Both operations require `MENU_MANAGE`; writes reuse staff session/CSRF protection.
A duplicate code returns 409, including collisions at the database unique constraint.
The existing public category APIs, Excel import and product/stock rules are unchanged.

In Menu & inventory workspace → Add product (or edit product details), use **+ New
category**. Creation keeps the product draft open and selects the new category.
The product editor cannot close or save during the category request. A created
category persists even if the operator subsequently cancels the product draft.
Refresh categories before retrying after an uncertain network failure: the category
may have been created even if its response was lost.

## Suggested initial taxonomy

| Code | Name | Order |
| --- | --- | --- |
| SWEETS | Sweets | 10 |
| BREAKFAST_PARATHAS | Breakfast & Parathas | 20 |
| SNACKS_FAST_FOOD | Snacks & Fast Food | 30 |
| THALIS_COMBOS | Thalis & Combos | 40 |
| PANEER_SABZI | Paneer & Sabzi | 50 |
| DAL_RICE | Dal & Rice | 60 |
| ROTI_BREADS | Roti & Breads | 70 |
| CAKES_PASTRIES | Cakes & Pastries | 80 |
| BEVERAGES | Beverages | 90 |

These are suggestions, not an automatic seed. Reuse existing import codes where
appropriate. Create each category once, then add products in the selected branch.

## DEV acceptance

1. Create a category on mobile and desktop before creating any products.
2. Confirm duplicate codes (including lowercase input) cannot create another row.
3. Open Add product, enter a name/code/price, create a category inline, and confirm
   the draft is retained and the new category selected. Save the product normally.
4. Verify a user without MENU_MANAGE cannot read/create categories.
5. Verify existing Excel imports still match the same category codes and existing
   product price, stock, service-hour and availability flows are unchanged.
6. Only promote to main after DEV acceptance. Rollback removes the new UI/API;
   categories already created remain valid for existing product/import flows.
