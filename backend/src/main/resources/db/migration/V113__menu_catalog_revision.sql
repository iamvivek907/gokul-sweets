-- A transactional revision covers imports, direct SQL, and every API replica.
CREATE TABLE menu_catalog_revision (id boolean PRIMARY KEY DEFAULT true CHECK(id), revision bigint NOT NULL DEFAULT 1, token uuid NOT NULL DEFAULT gen_random_uuid());
INSERT INTO menu_catalog_revision(id) VALUES(true);
CREATE FUNCTION advance_menu_catalog_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  UPDATE menu_catalog_revision SET revision=revision+1, token=gen_random_uuid() WHERE id=true;
  RETURN NULL;
END $$;
DO $$ DECLARE name text; BEGIN
  FOREACH name IN ARRAY ARRAY['branches','categories','products','branch_products','menu_service_policies','menu_service_items'] LOOP
    EXECUTE format('CREATE TRIGGER menu_catalog_changed AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON %I FOR EACH STATEMENT EXECUTE FUNCTION advance_menu_catalog_revision()', name);
  END LOOP;
END $$;
