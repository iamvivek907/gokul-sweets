/*
 * V47 created available_days_mask as SMALLINT, while the JPA entity maps
 * InventoryAutomationRule.availableDaysMask as Integer. Hibernate therefore
 * expects PostgreSQL INTEGER during schema validation.
 *
 * V47 may already be recorded in flyway_schema_history, so correct the schema
 * with a forward-only migration instead of editing the applied migration.
 */
ALTER TABLE inventory_automation_rules
    ALTER COLUMN available_days_mask
    TYPE INTEGER
    USING available_days_mask::INTEGER;
