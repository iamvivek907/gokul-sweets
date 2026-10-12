-- Presentation groups retain each portion's existing product, price and stock identity.
CREATE TABLE mobile_menu_config (branch_id BIGINT PRIMARY KEY REFERENCES branches(id), version BIGINT NOT NULL DEFAULT 0);
CREATE TABLE mobile_menu_groups (
 branch_id BIGINT NOT NULL REFERENCES mobile_menu_config(branch_id), group_key VARCHAR(40) NOT NULL,
 title VARCHAR(100) NOT NULL, position INTEGER NOT NULL,
 PRIMARY KEY(branch_id,group_key)
);
CREATE TABLE mobile_menu_choices (
 branch_id BIGINT NOT NULL, group_key VARCHAR(40) NOT NULL, product_id BIGINT NOT NULL REFERENCES products(id),
 label VARCHAR(30) NOT NULL, position INTEGER NOT NULL,
 PRIMARY KEY(branch_id,product_id), UNIQUE(branch_id,group_key,label),
 FOREIGN KEY(branch_id,group_key) REFERENCES mobile_menu_groups(branch_id,group_key) ON DELETE CASCADE
);
