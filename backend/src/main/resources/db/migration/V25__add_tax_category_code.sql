/*
 * Stable identity for tax categories used by menu import.
 */
ALTER TABLE tax_categories
    ADD COLUMN code VARCHAR(80);

UPDATE tax_categories
SET code = 'TAX_' || id
WHERE code IS NULL;

ALTER TABLE tax_categories
    ALTER COLUMN code SET NOT NULL;

ALTER TABLE tax_categories
    ADD CONSTRAINT uk_tax_categories_code
        UNIQUE (code);
