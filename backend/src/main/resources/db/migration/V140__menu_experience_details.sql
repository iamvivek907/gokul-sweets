-- Additive owner-authored details, never a source of prices or orderability.
CREATE TABLE branch_menu_details (
 branch_product_id BIGINT PRIMARY KEY REFERENCES branch_products(id) ON DELETE CASCADE,
 serving_info VARCHAR(120), ingredients VARCHAR(600), allergens VARCHAR(300),
 spice_level VARCHAR(12) CHECK (spice_level IN ('MILD','MEDIUM','HOT')),
 photo_focus_x SMALLINT NOT NULL DEFAULT 50 CHECK (photo_focus_x BETWEEN 0 AND 100),
 photo_focus_y SMALLINT NOT NULL DEFAULT 50 CHECK (photo_focus_y BETWEEN 0 AND 100),
 chef_pick BOOLEAN NOT NULL DEFAULT FALSE,
 version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0)
);
