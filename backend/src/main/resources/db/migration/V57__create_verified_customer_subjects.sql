-- SCRUM-36: identity subjects remain internal until ownership/reassignment rules are complete.
CREATE TABLE verified_customer_subjects (
    id UUID PRIMARY KEY,
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('DEV', 'PROD')),
    verified_phone VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_verified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT verified_customer_subjects_phone_format
        CHECK (verified_phone ~ '^\+91[6-9][0-9]{9}$'),
    CONSTRAINT verified_customer_subjects_environment_phone
        UNIQUE (environment, verified_phone),
    CONSTRAINT verified_customer_subjects_environment_id
        UNIQUE (environment, id)
);

COMMENT ON TABLE verified_customer_subjects IS
    'Internal verified phone registry. Phone possession alone does not grant access to older orders or consent.';
