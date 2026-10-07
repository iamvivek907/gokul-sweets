CREATE TABLE inventory_centre_jobs (
 id uuid PRIMARY KEY, branch_id bigint NOT NULL REFERENCES branches(id), staff_id bigint NOT NULL REFERENCES staff_users(id),
 digest varchar(64) NOT NULL, total integer NOT NULL, succeeded integer NOT NULL DEFAULT 0, failed integer NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE inventory_centre_tasks (
 id bigserial PRIMARY KEY, job_id uuid NOT NULL REFERENCES inventory_centre_jobs(id) ON DELETE CASCADE,
 product_id bigint NOT NULL REFERENCES products(id), status varchar(15) NOT NULL DEFAULT 'QUEUED' CHECK(status IN ('QUEUED','PROCESSING','SUCCEEDED','FAILED')),
 claim_token uuid, lease_until timestamptz, attempts integer NOT NULL DEFAULT 0, payload text, error varchar(500), finished_at timestamptz, UNIQUE(job_id,product_id)
);
CREATE INDEX inventory_centre_queue ON inventory_centre_tasks(id) WHERE status IN ('QUEUED','PROCESSING');
CREATE INDEX inventory_centre_history ON inventory_centre_jobs(branch_id,created_at DESC);
CREATE INDEX inventory_centre_expiry ON inventory_centre_tasks(finished_at,id) WHERE status='SUCCEEDED';
