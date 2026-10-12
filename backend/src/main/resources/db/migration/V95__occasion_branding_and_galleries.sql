ALTER TABLE occasion_packaging ADD COLUMN image_urls JSONB NOT NULL DEFAULT '[]';
CREATE TABLE occasion_branding (
 branch_id BIGINT PRIMARY KEY REFERENCES branches(id),
 headline VARCHAR(100) NOT NULL DEFAULT '', description VARCHAR(500) NOT NULL DEFAULT '',
 image_url VARCHAR(1000), published BOOLEAN NOT NULL DEFAULT FALSE
);
