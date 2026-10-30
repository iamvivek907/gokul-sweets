-- Existing meal prices remain unchanged. New automatic recipes use a zero base.
ALTER TABLE branch_meals ADD COLUMN pricing_mode varchar(20) NOT NULL DEFAULT 'FIXED'
 CHECK(pricing_mode IN ('FIXED','SELECTED_ITEMS'));
ALTER TABLE branch_meals ADD COLUMN maximum_discount numeric(12,2)
 CHECK(maximum_discount IS NULL OR maximum_discount>0);
ALTER TABLE branch_meals DROP CONSTRAINT branch_meals_base_price_check;
ALTER TABLE branch_meals ADD CONSTRAINT branch_meals_pricing_base_check
 CHECK((pricing_mode='FIXED' AND base_price>0) OR (pricing_mode='SELECTED_ITEMS' AND base_price=0));
