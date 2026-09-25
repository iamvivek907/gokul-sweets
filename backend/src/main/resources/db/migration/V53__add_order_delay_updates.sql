ALTER TABLE orders
    ADD COLUMN estimated_ready_at TIMESTAMP,
    ADD COLUMN delay_reason VARCHAR(300),
    ADD COLUMN delay_reported_at TIMESTAMP;
