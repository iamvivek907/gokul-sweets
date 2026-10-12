-- Browser-generated submission IDs survive a lost HTTP acknowledgement. Aliases also
-- recover an existing active job reused by the same requester/file/operation.
CREATE TABLE menu_import_submissions (
 id uuid PRIMARY KEY,
 job_id uuid NOT NULL REFERENCES menu_import_jobs(id) ON DELETE CASCADE,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX menu_import_submissions_job ON menu_import_submissions(job_id);
