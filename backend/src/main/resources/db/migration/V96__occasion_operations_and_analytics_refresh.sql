UPDATE customer_contacts cc SET verification_status='VERIFIED'
WHERE cc.verification_status='UNVERIFIED'
AND EXISTS (SELECT 1 FROM verified_customer_subjects v WHERE v.verified_phone=cc.normalized_phone);
CREATE TABLE analytics_refresh_checkpoint (
 id INTEGER PRIMARY KEY CHECK(id=1), highest_order_id BIGINT NOT NULL, latest_order_update TIMESTAMP,
 business_date DATE NOT NULL, refreshed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX orders_analytics_update ON orders(updated_at);
