CREATE TABLE branch_menu_appearance (
 branch_id bigint PRIMARY KEY REFERENCES branches(id), version bigint NOT NULL DEFAULT 0,
 draft jsonb NOT NULL DEFAULT '{"banners":[],"categories":[]}',
 live jsonb NOT NULL DEFAULT '{"banners":[],"categories":[]}', published_at timestamptz
);
