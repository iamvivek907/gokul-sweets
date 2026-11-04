CREATE TABLE occasion_packages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    branch_id BIGINT NOT NULL REFERENCES branches(id),
    box_id BIGINT NOT NULL REFERENCES occasion_packaging(id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(300) NOT NULL,
    image_url VARCHAR(1000),
    recipe JSONB NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_occasion_packages_branch ON occasion_packages(branch_id, id);
