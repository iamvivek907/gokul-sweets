CREATE TABLE branch_offering_settings (
    branch_id BIGINT PRIMARY KEY REFERENCES branches(id),
    edit_version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE branch_offerings (
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    scope VARCHAR(12) NOT NULL CHECK (scope IN ('DRAFT','PUBLISHED')),
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 11),
    title VARCHAR(80) NOT NULL,
    description VARCHAR(240) NOT NULL,
    PRIMARY KEY (branch_id, scope, position)
);
CREATE INDEX idx_reviews_public_branch ON reviews(branch_id, review_status, created_at DESC);
