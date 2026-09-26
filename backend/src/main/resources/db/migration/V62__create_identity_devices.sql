-- Random device identifiers are stored as digests and scoped to the deployment.
CREATE TABLE identity_devices (
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('DEV', 'PROD')),
    token_digest BYTEA NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (environment, token_digest)
);
CREATE INDEX idx_identity_devices_expiry ON identity_devices(expires_at);
