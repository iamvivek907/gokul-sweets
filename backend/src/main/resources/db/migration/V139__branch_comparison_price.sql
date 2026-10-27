ALTER TABLE branch_products ADD COLUMN comparison_price numeric(12,2);
ALTER TABLE branch_products ADD CONSTRAINT branch_comparison_price_positive CHECK (comparison_price IS NULL OR comparison_price > 0);
