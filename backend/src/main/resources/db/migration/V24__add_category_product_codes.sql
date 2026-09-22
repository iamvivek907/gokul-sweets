/*
 * =========================================================
 * V24 - Stable menu identity codes
 * =========================================================
 *
 * Purpose:
 * - Give categories and products stable import identities.
 * - Existing rows receive deterministic codes.
 * - Codes are unique and can later be used by Excel upsert.
 *
 * Existing records intentionally use ID-backed codes:
 *
 *   CATEGORY_1
 *   PRODUCT_15
 *
 * This avoids collisions caused by duplicate/similar names.
 * New records created through the future admin import will use
 * business-friendly codes supplied in the workbook.
 */

ALTER TABLE categories
    ADD COLUMN code VARCHAR(80);

ALTER TABLE products
    ADD COLUMN code VARCHAR(100);


/*
 * Backfill all existing rows.
 *
 * IDs are already unique and permanent, therefore these values
 * are deterministic and collision-safe.
 */
UPDATE categories
SET code = 'CATEGORY_' || id
WHERE code IS NULL;

UPDATE products
SET code = 'PRODUCT_' || id
WHERE code IS NULL;


/*
 * Codes become mandatory after backfill.
 */
ALTER TABLE categories
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE products
    ALTER COLUMN code SET NOT NULL;


/*
 * Database-level duplicate protection.
 */
ALTER TABLE categories
    ADD CONSTRAINT uk_categories_code
        UNIQUE (code);

ALTER TABLE products
    ADD CONSTRAINT uk_products_code
        UNIQUE (code);


/*
 * Explicit indexes are unnecessary because PostgreSQL creates
 * unique indexes for UNIQUE constraints automatically.
 */
