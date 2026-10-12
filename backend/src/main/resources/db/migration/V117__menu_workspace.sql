ALTER TABLE products ADD COLUMN workspace_version bigint NOT NULL DEFAULT 0;
ALTER TABLE branch_products ADD COLUMN workspace_version bigint NOT NULL DEFAULT 0;
CREATE FUNCTION bump_workspace_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.workspace_version := OLD.workspace_version + 1; RETURN NEW; END $$;
CREATE TRIGGER workspace_product_version BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION bump_workspace_version();
CREATE TRIGGER workspace_branch_version BEFORE UPDATE ON branch_products FOR EACH ROW EXECUTE FUNCTION bump_workspace_version();
CREATE TABLE menu_workspace_audit (
 id bigserial PRIMARY KEY, actor varchar(100) NOT NULL, branch_id bigint NOT NULL REFERENCES branches(id),
 product_id bigint REFERENCES products(id), action varchar(40) NOT NULL, before_state text, after_state text,
 changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX menu_workspace_audit_branch_time ON menu_workspace_audit(branch_id,changed_at DESC);
CREATE INDEX menu_workspace_product_name ON products(lower(name),id);
CREATE INDEX menu_workspace_branch_page ON branch_products(branch_id,display_order,id);
