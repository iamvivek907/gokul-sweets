-- SCRUM-36: a provider proof may establish at most one customer session.
CREATE TABLE verified_identity_proof_claims (
    proof_digest BYTEA PRIMARY KEY,
    environment VARCHAR(8) NOT NULL CHECK (environment IN ('DEV', 'PROD')),
    claimed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE verified_identity_proof_claims IS
    'SHA-256 digests of consumed MSG91 widget proofs; never persist raw access tokens.';
