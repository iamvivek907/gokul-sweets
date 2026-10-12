-- SCRUM-36: shared limits across application instances, with pseudonymous keys.
CREATE TABLE identity_exchange_limits (
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('DEV', 'PROD')),
    scope VARCHAR(8) NOT NULL CHECK (scope IN ('SOURCE', 'PHONE')),
    key_digest BYTEA NOT NULL,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL CHECK (attempts > 0),
    PRIMARY KEY (environment, scope, key_digest)
);

COMMENT ON TABLE identity_exchange_limits IS
    'HMAC digests for short identity exchange windows; purge expired rows regularly.';
