-- Row-local tokens avoid serialising unrelated reservations on a global counter.
ALTER TABLE inventory_daily_allocations ADD COLUMN availability_token uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE pickup_slots ADD COLUMN availability_token uuid NOT NULL DEFAULT gen_random_uuid();
CREATE FUNCTION refresh_availability_token() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.availability_token := gen_random_uuid();
  RETURN NEW;
END $$;
CREATE TRIGGER inventory_availability_changed BEFORE UPDATE ON inventory_daily_allocations
  FOR EACH ROW EXECUTE FUNCTION refresh_availability_token();
CREATE TRIGGER pickup_availability_changed BEFORE UPDATE ON pickup_slots
  FOR EACH ROW EXECUTE FUNCTION refresh_availability_token();
-- These are configuration changes, not high-frequency reservation writes.
DO $$ DECLARE name text; BEGIN
  FOREACH name IN ARRAY ARRAY['branch_inventory_policies','branch_pickup_settings','tax_categories'] LOOP
    EXECUTE format('CREATE TRIGGER menu_catalog_changed AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION advance_menu_catalog_revision()', name);
  END LOOP;
END $$;
