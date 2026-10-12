CREATE TABLE menu_import_jobs (
 id uuid PRIMARY KEY,
 branch_id bigint NOT NULL REFERENCES branches(id),
 staff_id bigint NOT NULL REFERENCES staff_users(id),
 operation varchar(12) NOT NULL CHECK(operation IN ('VALIDATE','IMPORT')),
 status varchar(12) NOT NULL DEFAULT 'QUEUED' CHECK(status IN ('QUEUED','PROCESSING','SUCCEEDED','FAILED')),
 filename varchar(255) NOT NULL,
 file_digest varchar(64) NOT NULL,
 payload bytea,
 result text,
 error varchar(500),
 attempts integer NOT NULL DEFAULT 0,
 claim_token uuid,
 lease_until timestamptz,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX menu_import_jobs_pending ON menu_import_jobs(created_at) WHERE status IN ('QUEUED','PROCESSING');
CREATE UNIQUE INDEX menu_import_jobs_branch_active ON menu_import_jobs(branch_id) WHERE status IN ('QUEUED','PROCESSING');
