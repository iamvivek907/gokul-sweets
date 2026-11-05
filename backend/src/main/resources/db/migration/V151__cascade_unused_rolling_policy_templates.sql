-- Removing an unused branch item also removes its automation selection.
-- Existing deletion checks still forbid deleting inventory/reservation history.
ALTER TABLE rolling_inventory_templates
    DROP CONSTRAINT rolling_inventory_templates_policy_id_fkey;
ALTER TABLE rolling_inventory_templates
    ADD CONSTRAINT rolling_inventory_templates_policy_id_fkey
    FOREIGN KEY (policy_id) REFERENCES branch_inventory_policies(id) ON DELETE CASCADE;
