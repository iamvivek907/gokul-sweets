CREATE TABLE storefront_app_icon (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    revision UUID NOT NULL,
    icon_192 BYTEA NOT NULL,
    icon_512 BYTEA NOT NULL,
    icon_maskable BYTEA NOT NULL,
    icon_apple BYTEA NOT NULL
);
