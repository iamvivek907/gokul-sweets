-- Shared item classification. Existing and newly imported products default to veg.
ALTER TABLE products ADD COLUMN vegetarian BOOLEAN NOT NULL DEFAULT TRUE;
