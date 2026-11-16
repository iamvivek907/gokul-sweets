CREATE TABLE staff_push_cancellations (
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('DEV', 'PROD')),
    staff_id BIGINT NOT NULL REFERENCES staff_users(id) ON DELETE CASCADE,
    registration_id UUID NOT NULL,
    PRIMARY KEY (environment, staff_id, registration_id)
);
