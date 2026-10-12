ALTER TABLE occasion_packaging ADD COLUMN capacity_grams INTEGER CHECK (capacity_grams IN (250,500,1000));
ALTER TABLE occasion_enquiries ADD COLUMN packing_groups JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(packing_groups)='array');
ALTER TABLE occasion_enquiry_items ADD COLUMN supplemental_grams NUMERIC(12,3) NOT NULL DEFAULT 0 CHECK (supplemental_grams>=0 AND supplemental_grams=trunc(supplemental_grams) AND (unit='PIECE' OR supplemental_grams=0));
ALTER TABLE occasion_production_allocations ADD COLUMN production_approved_at TIMESTAMPTZ, ADD COLUMN production_approved_by VARCHAR(150);
CREATE INDEX occasion_enquiry_service_date ON occasion_enquiries(environment,branch_id,service_date,created_at DESC,id DESC);

ALTER TABLE staff_order_alerts ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE staff_order_alerts ADD COLUMN enquiry_id UUID REFERENCES occasion_enquiries(id);
ALTER TABLE staff_order_alerts ADD CONSTRAINT staff_alert_source_check CHECK ((order_id IS NULL) <> (enquiry_id IS NULL));
ALTER TABLE staff_order_alerts DROP CONSTRAINT staff_order_alerts_kind_check;
ALTER TABLE staff_order_alerts ADD CONSTRAINT staff_order_alerts_kind_check CHECK (kind IN ('NEW_ORDER','PREPARATION_SOON','PREPARATION_DUE','PREPARATION_OVERDUE','READY_OVERDUE','NEW_OCCASION_REQUEST','OCCASION_ADVANCE_PAID'));
CREATE INDEX staff_alert_enquiry ON staff_order_alerts(environment,enquiry_id,id DESC);
