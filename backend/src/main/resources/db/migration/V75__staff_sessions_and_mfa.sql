CREATE TABLE staff_sessions (
    token_hash CHAR(64) PRIMARY KEY,
    staff_id BIGINT NOT NULL REFERENCES staff_users(id) ON DELETE CASCADE,
    csrf_hash CHAR(64) NOT NULL,
    staff_updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_staff_sessions_staff ON staff_sessions(staff_id) WHERE revoked_at IS NULL;
CREATE TABLE staff_mfa (
    staff_id BIGINT PRIMARY KEY REFERENCES staff_users(id) ON DELETE CASCADE,
    secret_ciphertext TEXT NOT NULL,
    last_counter BIGINT NOT NULL DEFAULT -1,
    enrolled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE staff_mfa_recovery (
    staff_id BIGINT NOT NULL REFERENCES staff_users(id) ON DELETE CASCADE,
    code_hash CHAR(64) NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (staff_id, code_hash)
);
CREATE TABLE staff_mfa_enrollments (
    token_hash CHAR(64) PRIMARY KEY,
    staff_id BIGINT NOT NULL REFERENCES staff_users(id) ON DELETE CASCADE,
    secret_ciphertext TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE staff_login_limits (
    username VARCHAR(100) PRIMARY KEY,
    failures INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE staff_auth_audit (
    id BIGSERIAL PRIMARY KEY,
    staff_id BIGINT REFERENCES staff_users(id) ON DELETE SET NULL,
    event VARCHAR(35) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
