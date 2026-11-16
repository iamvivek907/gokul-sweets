CREATE TABLE staff_push_setup_aliases (
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('DEV', 'PROD')),
    staff_id BIGINT NOT NULL REFERENCES staff_users(id) ON DELETE CASCADE,
    setup_id UUID NOT NULL,
    registration_id UUID NOT NULL REFERENCES staff_push_subscriptions(id) ON DELETE CASCADE,
    PRIMARY KEY (environment, staff_id, setup_id)
);
