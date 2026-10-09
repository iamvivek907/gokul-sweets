-- Receipts and cancellation tombstones outlive the single agent command mailbox.
CREATE TABLE print_station_action_receipts (
 branch_id BIGINT NOT NULL REFERENCES branches(id),
 station VARCHAR(50) NOT NULL,
 request_id UUID NOT NULL,
 outcome VARCHAR(10) NOT NULL CHECK (outcome IN ('ACCEPTED', 'CANCELLED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (branch_id, station, request_id)
);
