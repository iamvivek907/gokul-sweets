-- Readiness recovery checks one allocation's provenance, not the entire branch history.
-- Cover both audit predicates (count and system actor) with the allocation key first.
CREATE INDEX idx_inventory_plan_audit_allocation_actor
    ON inventory_allocation_plan_audit(allocation_id, performed_by);

-- Non-allocation transactions cannot affect an allocation's readiness provenance.
CREATE INDEX idx_inventory_transaction_allocation_history
    ON inventory_stock_transactions(allocation_id)
    INCLUDE (transaction_type, performed_by)
    WHERE allocation_id IS NOT NULL;
