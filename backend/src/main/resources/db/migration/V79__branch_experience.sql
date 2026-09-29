CREATE TABLE branch_experience (
    branch_id BIGINT PRIMARY KEY REFERENCES branches(id),
    draft_image_url TEXT,
    draft_mobile_url TEXT,
    draft_alt_text VARCHAR(180),
    draft_description VARCHAR(500),
    published_image_url TEXT,
    published_mobile_url TEXT,
    published_alt_text VARCHAR(180),
    published_description VARCHAR(500),
    edit_version BIGINT NOT NULL DEFAULT 0,
    published_revision BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE branch_experience_publications (
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    revision BIGINT NOT NULL,
    image_url TEXT,
    mobile_url TEXT,
    alt_text VARCHAR(180),
    description VARCHAR(500),
    actor_staff_id BIGINT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (branch_id, revision)
);

CREATE TABLE branch_experience_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    actor_staff_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    from_version BIGINT NOT NULL,
    to_version BIGINT NOT NULL,
    before_state TEXT,
    after_state TEXT,
    happened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
